`polymer.png` contains only a high-pass sample of the surface grain from the
user-supplied N73 reference (`1795526410057.png`). Low-frequency lighting and
phone features were removed, and row/column means were removed to prevent
visible tiling seams. The renderer mirrors the sample before repeating it.

`anodized.png` and `qd-satin.png` contain high-pass surface samples from the
supplied silver N8 and grey QD images. `legends/` holds only isolated lettering
coverage masks, including the N73 special symbols and QD numeric engravings.
`../prepare_phone_reference_materials.py` reproduces these from the originals.

The QD's current complete handset render is preserved as
`reference/qd-reference.webp`; the other supplied reference renders are stored
beside it. `../render_phone_shells.py` adds subtle matte grain outside each LCD,
exports touch masks for the actual key silhouettes, and derives depressed-key
textures from the supplied image. The navigation pad's nine states project the
complete QD cross-cap in perspective, with a stationary socket and contact
shadow. These are offline relief-rendered sprites, not a live 3D mesh.
