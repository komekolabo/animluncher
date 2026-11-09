# Fabrique etoile_ia.xml (le dessin) et etoile_ia_anime.xml (la boucle de 12 s) du widget « dossier animé »,
# Lancer : python3 outils/generer_avd.py app/src/main/res/drawable
# à partir des mêmes étapes que boucle_etoile.py, en palette CHAUDE (celle que Will a vue et validée).
import sys

D = 12000  # durée de la boucle, en ms

# nos propres raccourcis (res/interpolator/) : même chose que ceux d'Android, en plus court à écrire
INTERP = {'lin': '@interpolator/lin', 'acc': '@interpolator/acc', 'dec': '@interpolator/dec',
          'ad': '@interpolator/doux', 'over': '@interpolator/ressort'}

# palette chaude
ETOILE = ['#FF8C5B', '#FF6B9B', '#C4D13F']
TUILES = ['#FF8C5B', '#FF6B9B', '#C4D13F', '#FF7B6F']

def kfs(prop, cles):
    """cles = [(secondes, valeur, interpolateur pour arriver à cette clé)] -> un propertyValuesHolder."""
    if cles[0][0] > 0: cles = [(0, cles[0][1], 'lin')] + cles
    if cles[-1][0] < 12: cles = cles + [(12, cles[-1][1], 'lin')]
    lignes = [f'<propertyValuesHolder android:propertyName="{prop}" android:valueType="floatType">']
    vu = set()
    for t, v, f in cles:
        fr = round(t / 12, 4)
        while fr in vu: fr = round(fr + 0.0001, 4)
        vu.add(fr)
        it = '' if t == 0 else f' android:interpolator="{INTERP[f]}"'
        lignes.append(f'  <keyframe android:fraction="{fr}" android:value="{v}"{it} />')
    lignes.append('</propertyValuesHolder>')
    return '\n'.join(lignes)

def cible(nom, *holders, commentaire=''):
    c = f'    <!-- {commentaire} -->\n' if commentaire else ''
    return (c + f'    <target android:name="{nom}">\n        <aapt:attr name="android:animation">\n'
            f'            <objectAnimator android:duration="{D}" android:repeatCount="infinite" android:interpolator="@android:interpolator/linear">\n'
            + '\n'.join(holders) + '\n            </objectAnimator>\n        </aapt:attr>\n    </target>\n')

def etoile_path(cx, cy, r, p=0.085):
    q = r * p
    return (f"M{cx},{cy - r} Q{cx + q:.2f},{cy - q:.2f} {cx + r},{cy} Q{cx + q:.2f},{cy + q:.2f} {cx},{cy + r} "
            f"Q{cx - q:.2f},{cy + q:.2f} {cx - r},{cy} Q{cx - q:.2f},{cy - q:.2f} {cx},{cy - r} Z")

def degrade_lineaire(x0, y0, x1, y1, cols, attr='android:fillColor', ind='            '):
    items = ''.join(f'\n{ind}        <item android:offset="{o}" android:color="{c}" />' for o, c in cols)
    return (f'{ind}<aapt:attr name="{attr}">\n{ind}    <gradient android:type="linear" android:startX="{x0}" android:startY="{y0}" '
            f'android:endX="{x1}" android:endY="{y1}">{items}\n{ind}    </gradient>\n{ind}</aapt:attr>')

def dessin():
    x = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!-- LE DESSIN DU WIDGET « DOSSIER ANIMÉ » (09/11), fabriqué par un petit programme (aperçu de Will :',
         '     boucle_etoile.py, palette chaude). Repère 100 x 100 = tout le galet, centre (50, 48).',
         '     Chaque morceau a un nom : etoile_ia_anime.xml dit comment chacun bouge pendant la boucle de 12 s.',
         '     Au repos (avant l\'animation) on ne voit que l\'étoile ; les points, l\'anneau et les tuiles',
         '     commencent invisibles (taille 0 ou trait vide). -->',
         '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         '    xmlns:aapt="http://schemas.android.com/aapt"',
         '    android:width="62dp" android:height="62dp" android:viewportWidth="100" android:viewportHeight="100">',
         '',
         '    <!-- la lueur rose très douce derrière (jamais de flash) -->',
         '    <group android:name="lueur_g" android:pivotX="50" android:pivotY="48">',
         '        <path android:name="lueur" android:pathData="M50,10 A38,38 0 1,1 49.99,10 Z">',
         '            <aapt:attr name="android:fillColor">',
         '                <gradient android:type="radial" android:centerX="50" android:centerY="48" android:gradientRadius="38">',
         '                    <item android:offset="0" android:color="#40FF6B7A" />',
         '                    <item android:offset="0.45" android:color="#1AFF4B3A" />',
         '                    <item android:offset="1" android:color="#00FF4B3A" />',
         '                </gradient>',
         '            </aapt:attr>',
         '        </path>',
         '    </group>',
         '',
         '    <!-- 1) l\'étoile : elle tourne (etoile_tour) et change de taille (etoile_taille) -->',
         '    <group android:name="etoile_tour" android:pivotX="50" android:pivotY="48">',
         '        <group android:name="etoile_taille" android:pivotX="50" android:pivotY="48">',
         f'            <path android:pathData="{etoile_path(50, 48, 26)}">',
         degrade_lineaire(24, 22, 76, 74, [(0, ETOILE[0]), (0.5, ETOILE[1]), (1, ETOILE[2])], ind='                '),
         '            </path>',
         '        </group>',
         '    </group>',
         '',
         '    <!-- 2) l\'anneau : un cercle dont on ne trace qu\'un morceau (trimPathStart / trimPathEnd) -->',
         '    <group android:name="anneau_tour" android:pivotX="50" android:pivotY="48" android:rotation="-90">',
         '        <path android:name="anneau" android:pathData="M72,48 A22,22 0 1,1 28,48 A22,22 0 1,1 72,48"',
         '            android:strokeColor="#96FFFFFF" android:strokeWidth="1.4" android:strokeLineCap="round"',
         '            android:trimPathStart="0" android:trimPathEnd="0" />',
         '    </group>',
         '',
         '    <!-- 3) les 4 points (posés à 22 du centre, sur les diagonales) : le groupe « orbite » tourne, et le',
         '         groupe « rayon » grandit depuis le centre (les points jaillissent), puis rapetisse (ils filent',
         '         vers la place des tuiles, puis disparaissent au centre, cachés par les tuiles) -->',
         '    <group android:name="orbite" android:pivotX="50" android:pivotY="48">']
    x += ['        <group android:name="rayon" android:pivotX="50" android:pivotY="48" android:scaleX="0" android:scaleY="0">']
    for i in range(4):
        x += [f'            <group android:pivotX="50" android:pivotY="48" android:rotation="{90 * i - 45}">',
              f'                <path android:fillColor="#30{TUILES[i][1:]}" android:pathData="M78,48 A6,6 0 1,1 66,48 A6,6 0 1,1 78,48 Z" />',
              f'                <path android:fillColor="{TUILES[i]}" android:pathData="M75.4,48 A3.4,3.4 0 1,1 68.6,48 A3.4,3.4 0 1,1 75.4,48 Z" />',
              '            </group>']
    x += ['        </group>']
    x += ['    </group>', '',
          '    <!-- 4) les 4 tuiles (un petit dossier 2 x 2) : chacune se place (tuile_i) et change de taille (tuile_taille_i) -->']
    for i in range(4):
        col = TUILES[i]
        x += [f'    <group android:name="tuile_{i}">',
              f'        <group android:name="tuile_taille_{i}" android:pivotX="50" android:pivotY="48" android:scaleX="0" android:scaleY="0">',
              '            <path android:pathData="M46.4,41 L53.6,41 Q57,41 57,44.4 L57,51.6 Q57,55 53.6,55 L46.4,55 Q43,55 43,51.6 L43,44.4 Q43,41 46.4,41 Z"',
              '                android:strokeColor="#40FFFFFF" android:strokeWidth="0.8">',
              degrade_lineaire(50, 41, 50, 55, [(0, col), (1, '#AA' + col[1:])], ind='                '),
              '            </path>',
              '        </group>',
              '    </group>']
    x += ['</vector>', '']
    return '\n'.join(x)

def anime():
    import math
    morceaux = []
    # la lueur
    morceaux.append(cible('lueur', kfs('fillAlpha', [(3.0, 1, 'lin'), (3.6, 0.35, 'ad'), (10.4, 0.35, 'lin'), (11.2, 1, 'ad')]),
                          commentaire='la lueur faiblit pendant que les points et les tuiles jouent'))
    souffle = [(0, 1, 'lin'), (1.5, 1.06, 'ad'), (3.0, 1, 'ad'), (4.5, 1.06, 'ad'), (6.0, 1, 'ad'), (7.5, 1.06, 'ad'),
               (9.0, 1, 'ad'), (10.5, 1.06, 'ad'), (12, 1, 'ad')]
    morceaux.append(cible('lueur_g', kfs('scaleX', souffle), kfs('scaleY', souffle), commentaire='la lueur respire (toutes les 3 s)'))
    # l'étoile
    taille = [(0, 1, 'lin'), (1.5, 1.06, 'ad'), (3.0, 1, 'ad'), (3.6, 0, 'acc'), (10.4, 0, 'lin'), (11.2, 1, 'over'), (12, 1, 'lin')]
    morceaux.append(cible('etoile_taille', kfs('scaleX', taille), kfs('scaleY', taille),
                          commentaire="l'étoile respire, rétrécit en tournant vite (3 s), renaît avec un ressort (10,4 s)"))
    morceaux.append(cible('etoile_tour', kfs('rotation', [(0, 0, 'lin'), (3.0, 45, 'lin'), (3.6, 225, 'acc'), (3.7, -105, 'lin'),
                                                         (10.4, -105, 'lin'), (11.2, -15, 'dec'), (12, 0, 'lin')])))
    # l'anneau
    morceaux.append(cible('anneau', kfs('trimPathEnd', [(3.8, 0, 'lin'), (5.0, 1, 'ad'), (11.9, 1, 'lin'), (11.95, 0, 'lin')]),
                          kfs('trimPathStart', [(5.4, 0, 'lin'), (6.4, 1, 'ad'), (11.9, 1, 'lin'), (11.95, 0, 'lin')]),
                          commentaire="l'anneau se trace (3,8 s) puis s'efface (5,4 s) derrière les points"))
    morceaux.append(cible('anneau_tour', kfs('rotation', [(0, -90, 'lin'), (3.6, -90, 'lin'), (6.4, 180, 'ad'), (11.9, 180, 'lin'), (11.95, -90, 'lin')])))
    # les points
    morceaux.append(cible('orbite', kfs('rotation', [(3.6, 0, 'lin'), (6.4, 270, 'ad'), (11.9, 270, 'lin'), (11.95, 0, 'lin')]),
                          commentaire='les points font trois quarts de tour'))
    r = [(3.2, 0, 'lin'), (3.8, 1, 'over'), (6.4, 1, 'lin'), (7.0, 0.577, 'ad'), (7.4, 0, 'acc')]
    morceaux.append(cible('rayon', kfs('scaleX', r), kfs('scaleY', r), commentaire='les points jaillissent puis se rangent'))
    # les tuiles
    for i in range(4):
        a = math.radians(225 + 90 * i)
        dx, dy = round(12.7 * math.cos(a), 2), round(12.7 * math.sin(a), 2)
        d = 0.08 * i
        pos = lambda v: [(9.8 + d, v, 'lin'), (10.4 + d, 0, 'acc'), (11.9, 0, 'lin'), (11.95, v, 'lin')]
        morceaux.append(cible(f'tuile_{i}', kfs('translateX', [(0, dx, 'lin')] + pos(dx)), kfs('translateY', [(0, dy, 'lin')] + pos(dy)),
                              commentaire=f'tuile {i + 1} : sa place, puis elle rentre au centre' if i == 0 else ''))
        cles = [(6.8 + d, 0, 'lin'), (7.3 + d, 1, 'over')]
        for dep in (7.6, 8.7):
            s = dep + 0.18 * i
            cles += [(s, 1, 'lin'), (s + 0.25, 1.14, 'ad'), (s + 0.5, 1, 'ad')]
        cles += [(9.8 + d, 1, 'lin'), (10.4 + d, 0, 'acc')]
        morceaux.append(cible(f'tuile_taille_{i}', kfs('scaleX', cles), kfs('scaleY', cles),
                              commentaire='elle apparaît (ressort), ondule deux fois, puis disparaît' if i == 0 else ''))
    tete = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- LA BOUCLE DU WIDGET « DOSSIER ANIMÉ » (12 s, recommence sans fin), validée par Will sur l\'aperçu.\n'
            '     0-3 s : l\'étoile respire et tourne doucement. 3-3,6 s : elle tourne vite et rétrécit ; 4 points\n'
            '     jaillissent. 3,6-6,4 s : les points font trois quarts de tour en traçant un anneau qui s\'efface.\n'
            '     6,4-7,4 s : ils deviennent 4 tuiles. 7,6-9,7 s : une vague passe deux fois sur les tuiles.\n'
            '     9,8-10,6 s : les tuiles rentrent au centre. 10,4-11,2 s : l\'étoile renaît avec un ressort.\n'
            '     Une « keyframe » = la valeur à un instant (fraction de la boucle : 0,5 = 6 s), et l\'« interpolator »\n'
            '     dit comment on y arrive (en douceur, en accélérant, avec un petit dépassement…).\n'
            '     Fichier fabriqué par un programme : outils/generer_avd.py (le modifier là-bas, puis relancer). -->\n'
            '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:drawable="@drawable/etoile_ia">\n\n')
    return tete + '\n'.join(morceaux) + '</animated-vector>\n'

if __name__ == '__main__':
    dossier = sys.argv[1]
    open(f'{dossier}/etoile_ia.xml', 'w').write(dessin())
    open(f'{dossier}/etoile_ia_anime.xml', 'w').write(anime())
