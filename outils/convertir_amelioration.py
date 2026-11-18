# FABRIQUE L'OUTIL D'AMÉLIORATION DES IMAGES (18/11, voir AmeliorationImage.kt) : convertit un modèle Real-ESRGAN
# « compact » (SRVGGNetCompact) au format des téléphones (TFLite). Le fichier obtenu va dans la branche « modeles ».
#   poids d'origine : https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth
#   pip install tensorflow-cpu numpy   (PyTorch n'est PAS nécessaire : lire_pth.py lit le fichier de poids tout seul)
#   python3 convertir_amelioration.py realesr-general-x4v3.pth amelioration_photos_v1.tflite
# Le réseau est tout simple : une file de filtres 3x3 (« convolutions ») séparés par des PReLU, puis un
# « pixel shuffle » qui transforme 48 valeurs par point en un carré de 4 x 4 points de couleur, ajouté à
# l'image agrandie bêtement (plus proche voisin). On le reconstruit couche par couche avec les poids d'origine.
import sys

import numpy as np
import tensorflow as tf

from lire_pth import lire

COTE = 192      # le modèle traite des carreaux de 192 x 192 et rend des carreaux de 768 x 768
ECHELLE = 4


def construire(poids):
    convs = sorted({int(k.split(".")[1]) for k, v in poids.items() if v.ndim == 4})
    entree = tf.keras.Input(shape=(COTE, COTE, 3), batch_size=1, name="image")
    x = entree
    for n, i in enumerate(convs):
        w = poids[f"body.{i}.weight"].transpose(2, 3, 1, 0)     # PyTorch (sortie, entrée, h, l) -> TF (h, l, entrée, sortie)
        b = poids[f"body.{i}.bias"]
        dernier = n == len(convs) - 1
        if dernier:
            # PyTorch range les 48 sorties par (couleur, ligne, colonne), TensorFlow par (ligne, colonne, couleur)
            c = w.shape[3] // (ECHELLE * ECHELLE)
            ordre = [coul * ECHELLE * ECHELLE + pos for pos in range(ECHELLE * ECHELLE) for coul in range(c)]
            w, b = w[..., ordre], b[ordre]
        couche = tf.keras.layers.Conv2D(w.shape[3], 3, padding="same", name=f"conv{i}")
        x = couche(x)
        couche.set_weights([w, b])
        if not dernier:
            a = poids[f"body.{i + 1}.weight"]
            prelu = tf.keras.layers.PReLU(shared_axes=[1, 2], name=f"prelu{i + 1}")
            x = prelu(x)
            prelu.set_weights([a.reshape(1, 1, -1)])
    x = tf.keras.layers.Lambda(lambda t: tf.nn.depth_to_space(t, ECHELLE), name="pixel_shuffle")(x)
    base = tf.keras.layers.UpSampling2D(ECHELLE, interpolation="nearest", name="agrandi")(entree)
    x = tf.keras.layers.Add()([x, base])
    x = tf.keras.layers.Lambda(lambda t: tf.clip_by_value(t, 0.0, 1.0), name="borne")(x)
    return tf.keras.Model(entree, x)


def main(source, cible):
    brut = lire(source)
    poids = brut.get("params_ema") or brut.get("params") or brut
    modele = construire(poids)
    conv = tf.lite.TFLiteConverter.from_keras_model(modele)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]      # poids rangés en demi-précision : fichier 2 fois plus petit
    donnees = conv.convert()
    open(cible, "wb").write(donnees)
    # contrôle : le fichier converti doit donner (presque) la même image que le réseau d'origine
    essai = np.random.RandomState(0).rand(1, COTE, COTE, 3).astype(np.float32)
    ref = modele(essai).numpy()
    it = tf.lite.Interpreter(model_content=donnees)
    it.allocate_tensors()
    e, s = it.get_input_details()[0], it.get_output_details()[0]
    it.set_tensor(e["index"], essai)
    it.invoke()
    print(cible, len(donnees), "octets ; entrée", e["shape"], e["dtype"].__name__, "; sortie", s["shape"],
          "; écart max", float(np.abs(it.get_tensor(s["index"]) - ref).max()))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
