"""Builds the Bedrock resource pack (src/main/resources/bedrock/UltraRevive.mcpack) from the Java pack
(src/main/resources/resourcepack.zip), so both editions show the same three totem textures.

Bedrock has no emissive layer, so each *_e.png (the glowing eyes) is painted over its base texture.
The Geyser mapping is NOT built here: the plugin writes it at startup from config.yml (custom-model-data
and material are configurable). Run after changing any totem texture:   python tools/bedrock_pack.py
"""
import io
import json
import os
import zipfile

from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(AQUI, "..", "src", "main", "resources")
TEX = "assets/minecraft/textures/item/ultrarevivir/"
# Java texture -> Bedrock identifier (ultrarevive:<id>); the plugin's BedrockPack uses the same ids
TOTEMS = {"totem_dios": "aether_totem", "totem_inframundo": "nether_totem", "totem_end": "end_totem"}
VERSION = [1, 0, 1]
# fixed UUIDs: Bedrock clients cache packs by UUID + version, so they must never change between builds
HEADER_UUID = "6f1d2a3b-8c4e-4b7a-9d21-5e0c7a1b2f31"
MODULE_UUID = "a2c94e17-3b5d-4f68-8e0a-91d7c6b4e352"


def png(im):
    b = io.BytesIO()
    im.save(b, "PNG", optimize=True)
    return b.getvalue()


def main():
    java = zipfile.ZipFile(os.path.join(RES, "resourcepack.zip"))
    os.makedirs(os.path.join(RES, "bedrock"), exist_ok=True)
    salida = os.path.join(RES, "bedrock", "UltraRevive.mcpack")
    manifest = {
        "format_version": 2,
        "header": {"name": "UltraRevive Totems", "description": "Textures of UltraRevive's three totems · Social Studio",
                   "uuid": HEADER_UUID, "version": VERSION, "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": MODULE_UUID, "version": VERSION}],
    }
    textura = {"resource_pack_name": "ultrarevive", "texture_name": "atlas.items", "texture_data": {}}
    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        icono = Image.open(io.BytesIO(java.read("pack.png"))).convert("RGBA").resize((256, 256), Image.LANCZOS)
        z.writestr("pack_icon.png", png(icono))
        for java_name, bid in TOTEMS.items():
            base = Image.open(io.BytesIO(java.read(TEX + java_name + ".png"))).convert("RGBA")
            emis = Image.open(io.BytesIO(java.read(TEX + java_name + "_e.png"))).convert("RGBA")
            base.alpha_composite(emis)
            z.writestr(f"textures/items/ultrarevive/{bid}.png", png(base))
            textura["texture_data"][f"ultrarevive.{bid}"] = {"textures": [f"textures/items/ultrarevive/{bid}"]}
        z.writestr("textures/item_texture.json", json.dumps(textura, indent=2))
    print("listo:", os.path.normpath(salida), os.path.getsize(salida), "bytes")


if __name__ == "__main__":
    main()
