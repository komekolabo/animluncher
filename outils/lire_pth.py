# Lit un fichier de poids PyTorch (.pth) SANS PyTorch : c'est une archive zip qui contient une « recette »
# (pickle) et les tableaux de nombres bruts. On ne reconstruit que des tableaux numpy, rien d'autre.
import pickle
import sys
import zipfile

import numpy as np

TYPES = {"FloatStorage": np.float32, "HalfStorage": np.float16, "DoubleStorage": np.float64,
         "LongStorage": np.int64, "IntStorage": np.int32}


class _Stockage:
    def __init__(self, nom):
        self.dtype = TYPES[nom]


def _tenseur(stockage, decalage, taille, pas, *_):
    tableau, dtype = stockage
    n = int(np.prod(taille)) if len(taille) else 1
    return np.frombuffer(tableau, dtype=dtype, count=n, offset=decalage * np.dtype(dtype).itemsize).reshape(taille).copy()


class _Lecteur(pickle.Unpickler):
    def __init__(self, f, archive, racine):
        super().__init__(f)
        self.archive, self.racine = archive, racine

    # On n'autorise QUE les quelques noms nécessaires (un pickle peut sinon lancer n'importe quel code).
    def find_class(self, module, nom):
        if module == "torch._utils" and nom == "_rebuild_tensor_v2":
            return _tenseur
        if module == "torch" and nom in TYPES:
            return _Stockage(nom)
        if module == "collections" and nom == "OrderedDict":
            import collections
            return collections.OrderedDict
        raise pickle.UnpicklingError(f"nom refusé : {module}.{nom}")

    def persistent_load(self, pid):
        _, stockage, cle, _, _ = pid
        return (self.archive.read(f"{self.racine}/data/{cle}"), stockage.dtype)


def lire(chemin):
    with zipfile.ZipFile(chemin) as z:
        pkl = next(n for n in z.namelist() if n.endswith("data.pkl"))
        racine = pkl.rsplit("/", 1)[0]
        with z.open(pkl) as f:
            return _Lecteur(f, z, racine).load()


if __name__ == "__main__":
    d = lire(sys.argv[1])
    print(list(d.keys())[:5])
    poids = d.get("params_ema") or d.get("params") or d
    for k, v in poids.items():
        print(k, v.shape, v.dtype)
