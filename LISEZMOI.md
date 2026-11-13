# Modèles téléchargés par AnimEngine

Cette branche ne contient PAS de code : seulement des fichiers que l'app télécharge à la demande.

- `detourage_dessins_v1.tflite` (44 Mo) : détourage des dessins / anime pour le fond 3D.
  Conversion TFLite (poids 8 bits) du modèle « anime-segmentation » de SkyTNT
  (https://github.com/SkyTNT/anime-segmentation, licence Apache 2.0, fichier isnetis.onnx).
  Entrée [1, 1024, 1024, 3] float32 (RGB / 255), sortie [1, 1024, 1024, 1] (masque 0 à 1).
  Téléchargé par l'app la première fois qu'on détoure un dessin (voir DetourageDessins.kt).
