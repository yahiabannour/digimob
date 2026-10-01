"""
******************************************************************************
 file:      tools/blender_demos.py
 brief:     Erzeugt die 3D-Demos der Uebungen (demos/<uebung>.mp4) mit Blender: eine Trainerin
            (Mixamo-Figur mit ihren eigenen Materialien) macht jede Uebung in einem einfachen
            Therapieraum ein paar Mal vor. Die App zeigt das Video
            waehrend der Einfuehrung statt des Beispielbilds (uebungslauf.py).
 software:  Blender (Version: 5.2), laeuft in Blenders eigenem Python, nicht im venv
 ext. libs: Figur von Mixamo (Adobe, frei nutzbar, die Datei selbst darf nicht weitergegeben werden)
 info:      Aufruf aus sportuebungen/ (oder von ueberall):
              blender -b -P tools/blender_demos.py                  alle Uebungen
              blender -b -P tools/blender_demos.py -- butterfly     nur diese
            Schreibt demos/<uebung>.mp4 (1200x900, 30 fps, H.264) und demos/demos.blend (zum
            Nachbearbeiten, git-ignored).
            Voraussetzung: die Figur CHARACTER (git-ignored, von mixamo.com: Charakter "Ch37",
            Download als FBX Binary, With Skin, T-Pose) unter demos/assets/mixamo/.
            In einem laufenden Blender (z.B. ueber den Blender-MCP) nur aufbauen, ohne zu rendern:
              ns = runpy.run_path("tools/blender_demos.py", run_name="blender_demos")
              scene = ns["build_scene"](); ns["animate"](scene, "abduktion")
            Alles liegt in einer eigenen Szene (SCENE), andere Szenen bleiben unberuehrt.
            Die Figur schaut zur Kamera (-y), ihre linke Seite ist +x. Gerendert wird wie eine
            normale Kamera; die App spiegelt das Video wie das Kamerabild.
            Bewegungen: EXERCISES, Winkel in Grad, relativ zum Elternteil, bezogen auf eine Haltung
            mit senkrecht haengenden Armen und Beinen. Schulter/Huefte (Euler XYZ): x < 0 nach vorne,
            y < 0 seitlich nach aussen (links; rechts wird gespiegelt), z > 0 nach aussen drehen.
            Ellbogen/Handgelenk (Euler ZYX: erst verdrehen, dann beugen): x < 0 beugen, z < 0 die
            Handflaeche nach hinten drehen. Kopf (neck): z > 0 nach links. twist (nur z): dreht den
            Oberarm um die eigene Achse, bevor die Schulter ihn hebt (z < 0 nach innen); der Unterarm
            dreht mit, sein z gleicht das wieder aus. Aendert keinen Winkel des Zaehlers, aber die
            Haut: je weiter der Oberarm insgesamt von der T-Pose weg gedreht ist, desto mehr loest
            sich die Schulter optisch vom Rumpf (Flexion ohne twist: 177 Grad, Abduktion: 103 Grad).
            Die Gelenke sind unsichtbare Empties (JOINTS); jeder Knochen des Mixamo-Skeletts kopiert
            die Drehung seines Gelenks (BONES, Copy-Rotation-Constraints), so bleiben die Winkel
            unabhaengig vom Skelett. Die Schluesselbeine folgen dem Arm (girdle()), sonst drueckt
            ein gehobener Arm die Schulter in den Ruecken.
            Die Kamera stellt sich pro Uebung so weit weg, dass die ganze Figur mit Haenden,
            Fuessen und Props in jedem Frame im Bild ist (fit_camera()).
******************************************************************************
"""
import math
import os
import sys

import bmesh
import bpy
from mathutils import Euler, Matrix, Vector

APP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEMO_DIR = os.path.join(APP_DIR, "demos")
CHARACTER = os.path.join(DEMO_DIR, "assets", "mixamo", "Ch37_nonPBR.fbx")

SCENE = "DigiMob_Demo"
FPS = 30
RESOLUTION = (1200, 900)    # so gross zeigt die App das Bild (uebungslauf.DEMO_SIZE), 4:3 wie die Kamera
PREFIX = "Demo_"            # Namen aller Objekte der Szene
MAT_HEIGHT = 0.012          # die Figur steht auf der Matte
MATTE = {"Metallic": 0.0, "Specular IOR Level": 0.3, "Roughness": 0.7}     # siehe matte()
# Die Figur etwas fuelliger (fuller()): um diesen Anteil waechst ihr Abstand zur Koerperachse
FULLER = {"belly": 0.32, "waist": 0.18, "back": 0.10, "hips": 0.05, "chest": 0.07, "thigh": 0.06, "arm": 0.15}
JACKET = {"Saturation": 0.3, "Value": 0.06}    # die rosa Jacke dunkel (dark_jacket())

# Gelenk -> Knochen des Mixamo-Skeletts (ohne das Praefix "mixamorig<n>:"), die seine Drehung uebernehmen
BONES = {"torso": ["Spine", "Spine1", "Spine2"], "neck": ["Neck", "Head"]}
for _s, _n in (("L", "Left"), ("R", "Right")):
    BONES.update({f"clavicle_{_s}": [f"{_n}Shoulder"], f"twist_{_s}": [f"{_n}Arm"],
                  f"elbow_{_s}": [f"{_n}ForeArm"], f"wrist_{_s}": [f"{_n}Hand"],
                  f"hip_{_s}": [f"{_n}UpLeg"], f"knee_{_s}": [f"{_n}Leg"], f"ankle_{_s}": [f"{_n}Foot"]})
# Die Figur steht in der T-Pose; diese Knochen werden fuer die Winkel 0 senkrecht nach unten gedreht
HANGING = {f"{n}{b}" for n in ("Left", "Right") for b in ("Arm", "ForeArm", "Hand", "UpLeg", "Leg")}
FINGERS = ("Index", "Middle", "Ring", "Pinky", "Thumb")

# Gelenk -> Elterngelenk (root ist die Figur)
JOINTS = {"torso": "root", "neck": "torso", "hip_L": "root", "hip_R": "root"}
for _s in "LR":
    JOINTS.update({f"clavicle_{_s}": "torso", f"shoulder_{_s}": "torso", f"twist_{_s}": f"shoulder_{_s}",
                   f"elbow_{_s}": f"twist_{_s}",
                   f"wrist_{_s}": f"elbow_{_s}", f"knee_{_s}": f"hip_{_s}", f"ankle_{_s}": f"knee_{_s}"})
TWIST_FIRST = ("elbow", "wrist")        # Euler ZYX: erst um die eigene Achse drehen, dann beugen
# Schluesselbein zum Arm, siehe girdle()
GIRDLE = {"start": 45, "ratio": 0.3, "top": 28, "reach": 15}

# Flache Farben (linear RGB) fuer Raum und Props
COLORS = {
    "floor": (0.62, 0.40, 0.22),
    "floor_dark": (0.52, 0.32, 0.17),
    "wall": (0.50, 0.70, 0.74),
    "wall_side": (0.42, 0.62, 0.68),
    "white": (0.85, 0.85, 0.82),
    "sky": (0.55, 0.80, 1.00),
    "wood": (0.70, 0.45, 0.22),
    "pot": (0.75, 0.30, 0.15),
    "leaves": (0.12, 0.45, 0.12),
    "ball": (0.85, 0.25, 0.45),
    "mat": (0.05, 0.22, 0.75),
    "metal": (0.45, 0.46, 0.50),
    "plate": (0.08, 0.08, 0.10),
}

# Kamera je Uebung: Drehung um die Figur (Grad, > 0 von ihrer linken Seite), Neigung nach unten (Grad).
# Den Abstand bestimmt fit_camera().
VIEWS = {
    "full": (0, 4),
    "oblique": (35, 4),
}
MARGIN = 0.07               # Rand um die Figur, Anteil der Bildhoehe


# ---------- Posen ----------

def side(s, **joints):
    """Gelenkwinkel einer Seite ("L" oder "R"), fuer die linke Seite angegeben: rechts wird y und z
    gespiegelt, damit dieselben Zahlen dieselbe Bewegung auf beiden Seiten sind."""
    return {f"{j}_{s}": rot if s == "L" else mirror(rot) for j, rot in joints.items()}


def both(**joints):
    return {**side("L", **joints), **side("R", **joints)}


def mirror(rot):
    return (rot[0], -rot[1], -rot[2])


# Grundhaltung: aufrecht, Arme haengen leicht vom Koerper weg und leicht gebeugt, Fuesse etwas auseinander
REST = {
    "torso": (0, 0, 0),
    "neck": (0, 0, 0),
    **both(shoulder=(0, -6, 0), twist=(0, 0, 0), elbow=(-8, 0, 0), wrist=(0, 0, 0),
           hip=(0, -3, 0), knee=(0, 0, 0), ankle=(0, 3, 0)),
}


def girdle(shoulder, start, ratio, top, reach):
    """Schluesselbein zur Schulter (linke Seite): hebt sich, wenn der Arm ueber start Grad steigt (ratio
    davon, hoechstens top Grad), und schiebt die Schulter nach vorne/hinten, wenn der Arm dorthin zeigt."""
    arm = Euler([math.radians(a) for a in shoulder], "XYZ").to_matrix() @ Vector((0, 0, -1))
    elevation = math.degrees(math.acos(max(-1.0, min(1.0, -arm.z))))
    forward = -arm.y * math.sqrt(max(0.0, 1 - arm.z * arm.z))
    return (0, -min(top, ratio * max(0, elevation - start)), -reach * forward)


def full_pose(pose):
    """REST + pose mit den Schluesselbeinen zu den Schultern."""
    pose = {**REST, **pose}
    pose["clavicle_L"] = girdle(pose["shoulder_L"], **GIRDLE)
    pose["clavicle_R"] = mirror(girdle(mirror(pose["shoulder_R"]), **GIRDLE))
    return pose


def timeline(peaks, move=1.0, hold=0.4, rest=0.4, via_rest=True, via=None):
    """[(t, pose)]: aus der Grundhaltung ({}) zu jeder Pose in peaks, dort halten, und (via_rest)
    zurueck. Beginnt und endet in der Grundhaltung, damit das Video nahtlos wiederholt. via: Pose auf
    halbem Weg hin und zurueck (nur mit via_rest)."""
    t = rest
    keys = [(0.0, {}), (t, {})]
    for peak in peaks:
        if via:
            keys.append((t + move / 2, via))
        t += move
        keys.append((t, peak))
        t += hold
        keys.append((t, peak))
        if via_rest:
            if via:
                keys.append((t + move / 2, via))
            t += move
            keys.append((t, {}))
            t += rest
            keys.append((t, {}))
    if not via_rest:
        t += move
        keys.append((t, {}))
        t += rest
        keys.append((t, {}))
    return keys


# Arm nach aussen gedreht, die Handflaeche zeigt zur Seite hin nach oben: 45 + 90 + 45 Grad
TURN_OUT = dict(twist=(0, 0, 45), elbow=(-5, 0, 90), wrist=(0, 0, 45))

# Stab im Obergriff: Unterarm und Hand drehen die Handflaeche nach hinten
OVERHAND = dict(elbow=(-10, 0, -50), wrist=(0, 0, -40))

# Pro Uebung: base = Grundhaltung dieser Uebung (ueber REST), keys = Zeitleiste, props, Kamera,
# fist = Faust (mit Hanteln/Stab). Die Ausschlaege gehen deutlich ueber die Schwellen der Zaehler hinaus.
EXERCISES = {
    # Arme seitlich bis ueber den Kopf, die Haende fast in der Mitte (Zaehler: > 150 Grad;
    # ueber 180 Grad zeigt der Arm nach innen)
    # Auf dem Weg nach oben dreht der Arm nach aussen: in Schulterhoehe zeigen die Handflaechen nach
    # oben, ueber dem Kopf zueinander. Die Drehung teilen sich Oberarm (twist), Unterarm und Hand,
    # sonst verdreht die Haut an der Schulter (siehe twist im Kopf)
    "abduktion": {
        "keys": timeline([both(shoulder=(0, -188, 0), **TURN_OUT)] * 3,
                         via=both(shoulder=(0, -92, 0), **TURN_OUT)),
    },
    # Arme seitlich bis 90 Grad (Zaehler: > 70 Grad)
    "seitenheben": {
        "keys": timeline([both(shoulder=(0, -92, 0), elbow=(-3, 0, 0))] * 3),
    },
    # Stab schulterbreit vor den Beinen, gestreckt nach vorne bis ueber den Kopf (Zaehler: > 140 Grad)
    "flexion_schulter": {
        "base": both(shoulder=(-20, 0, 0), **OVERHAND),
        # Oben den Oberarm 120 Grad nach innen drehen, den Unterarm zurueck: der Griff bleibt gleich
        "keys": timeline([both(shoulder=(-168, 0, 0), twist=(0, 0, -120), elbow=(-3, 0, 70), wrist=(0, 0, -40))] * 3,
                         move=1.2),
        "props": ["stick"],
        "fist": True,
    },
    # Leicht vorgebeugt pendeln die Arme abwechselnd nach vorne und hinten
    "mobilisation_schulter": {
        "base": {"torso": (12, 0, 0), **both(shoulder=(-12, -6, 0))},
        "keys": timeline([{**side("L", shoulder=(-90, -6, 0)), **side("R", shoulder=(35, -6, 0))},
                          {**side("R", shoulder=(-90, -6, 0)), **side("L", shoulder=(35, -6, 0))}] * 3,
                         move=1.1, hold=0.15, via_rest=False),
        "view": "oblique",
    },
    # Unterarme abwechselnd, dann beide zur Schulter (Zaehler: Ellbogen < 110 Grad)
    "bizepscurls": {
        "keys": timeline([side("L", elbow=(-140, 0, 0)), side("R", elbow=(-140, 0, 0))] * 2
                         + [both(elbow=(-140, 0, 0))], move=0.8, hold=0.3, rest=0.3),
        "props": ["dumbbells"],
        "fist": True,
    },
    # Oberarme knapp unter Schulterhoehe, Unterarme senkrecht: Ellbogen vorne zusammen -> zur Seite auf
    "butterfly": {
        "base": both(shoulder=(-72, 0, -18), elbow=(-108, 0, 0)),
        "keys": timeline([both(shoulder=(-72, 0, 85), elbow=(-108, 0, 0))] * 3),
        "props": ["dumbbells"],
        "fist": True,
    },
    # Oberarme am Koerper, Ellbogen 90 Grad: Unterarme nach aussen drehen
    "rotation": {
        "base": both(shoulder=(0, -4, 0), elbow=(-90, 0, 0)),
        "keys": timeline([both(shoulder=(0, -4, 70), elbow=(-90, 0, 0))] * 3),
    },
    # Kopf nach links und nach rechts drehen (Zaehler: > 35 Grad)
    "halswirbelsaeule": {
        "keys": timeline([{"neck": (0, 0, 60)}, {"neck": (0, 0, -60)}] * 2, hold=0.5),
    },
}


# ---------- Szene ----------

def material(name, color, roughness=0.7):
    mat = bpy.data.materials.new(PREFIX + name)
    bsdf = next(n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    bsdf.inputs["Roughness"].default_value = roughness
    mat.diffuse_color = (*color, 1.0)
    return mat


def matte(mat):
    """Die Materialien der Mixamo-Figur kommen aus dem FBX-Import halb metallisch, mit Specular- und
    Glanz-Map (als Roughness gelesen): Haut und Jacke glaenzen. MATTE ersetzt diese Werte."""
    bsdf = next(n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    for name, value in MATTE.items():
        for link in [l for l in mat.node_tree.links if l.to_socket == bsdf.inputs[name]]:
            mat.node_tree.links.remove(link)
        bsdf.inputs[name].default_value = value


def dark_jacket(obj):
    """Die Jacke teilt sich die Textur mit Haut und Hose; sie bekommt eine eigene Kopie des Materials,
    deren Farbe JACKET entsaettigt und abdunkelt."""
    mat = obj.material_slots[0].material.copy()
    obj.material_slots[0].material = mat
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    link = next(l for l in links if l.to_socket == bsdf.inputs["Base Color"])
    hsv = nodes.new("ShaderNodeHueSaturation")
    for name, value in JACKET.items():
        hsv.inputs[name].default_value = value
    links.new(link.from_socket, hsv.inputs["Color"])
    links.new(hsv.outputs["Color"], bsdf.inputs["Base Color"])


def smoothstep(e0, e1, x):
    t = min(max((x - e0) / (e1 - e0), 0.0), 1.0)
    return t * t * (3 - 2 * t)


def bump(x, a, b, c, d):
    """0 unter a, steigt bis b auf 1, bleibt 1 bis c, faellt bis d auf 0."""
    return smoothstep(a, b, x) * (1 - smoothstep(c, d, x))


def fuller(meshes, armature, prefix):
    """Macht die Figur in der T-Pose (vor dem Rig) fuelliger: Bauch, Huefte, Oberschenkel und Oberarme
    werden von ihrer Achse weg gestreckt (Rumpf: senkrecht durch die Huefte, Arme und Beine: die
    Knochen). Der Abstand waechst um einen Anteil (FULLER), nicht um einen festen Betrag, so bleibt
    die Jacke ueber Hose und Reissverschluss in derselben Lage. Die Haut der Figur ist nur Kopf,
    Hals und Haende; Rumpf, Arme und Beine sind Jacke und Hose."""
    def head(name):
        return armature.matrix_world @ armature.data.bones[prefix + name].head_local
    f = FULLER
    hips, spine1, spine2, neck = (head(b).z for b in ("Hips", "Spine1", "Spine2", "Neck"))
    center_y = head("Hips").y
    armpit = head("LeftArm").z - 0.08
    shoulder_x = head("LeftArm").x
    limbs = [(head(f"{n}UpLeg"), head(f"{n}Leg"), "thigh", (-0.3, 0.0, 0.3, 1.0)) for n in ("Left", "Right")]
    limbs += [(head(f"{n}Arm"), head(f"{n}ForeArm"), "arm", (-0.1, 0.25, 0.6, 1.0)) for n in ("Left", "Right")]

    def offset(p):
        # Rumpf: vorne der Bauch, an den Seiten Taille und Huefte, hinten etwas Ruecken und Po
        belly = bump(p.z, hips - 0.08, hips + 0.03, spine1, spine2 + 0.02)
        seat = bump(p.z, hips - 0.25, hips - 0.12, hips - 0.02, hips + 0.08)
        chest = bump(p.z, spine1, spine2, spine2 + 0.08, neck - 0.02)
        front = f["belly"] * belly + 0.2 * f["belly"] * seat + f["chest"] * chest
        sides = f["waist"] * belly + f["hips"] * seat + 0.5 * f["chest"] * chest
        back = f["back"] * belly + 0.3 * f["hips"] * seat + 0.5 * f["chest"] * chest
        # die Arme in der T-Pose gehoeren nicht zum Rumpf
        torso = 1 - smoothstep(armpit - 0.05, armpit + 0.02, p.z) * smoothstep(0.8 * shoulder_x, 1.1 * shoulder_x, abs(p.x))
        dy = p.y - center_y
        d = Vector((p.x * sides, dy * (front if dy < 0 else back), 0.0)) * torso
        # Oberschenkel und Oberarme: weg von ihrem Knochen, zur Mitte (Schritt) hin ausgeblendet
        for top, bottom, part, profile in limbs:
            if (p.x > 0) != (top.x > 0):
                continue
            axis = bottom - top
            t = (p - top).dot(axis) / axis.length_squared
            radial = p - (top + axis * t)
            if radial.length > 0.2:
                continue
            k = f[part] * bump(t, *profile)
            if part == "thigh":
                k *= smoothstep(0.0, 0.04, abs(p.x))
            else:
                k *= smoothstep(armpit - 0.1, armpit, p.z) if abs(p.x) < shoulder_x else 1.0
            d += radial * k
        return d

    for obj in meshes:
        world, local = obj.matrix_world, obj.matrix_world.inverted()
        for v in obj.data.vertices:
            p = world @ v.co
            v.co = local @ (p + offset(p))
        obj.data.update()


def mesh_object(scene, name, bm, mat, parent=None):
    mesh = bpy.data.meshes.new(PREFIX + name)
    bm.to_mesh(mesh)
    bm.free()
    mesh.materials.append(mat)
    obj = bpy.data.objects.new(PREFIX + name, mesh)
    scene.collection.objects.link(obj)
    obj.parent = parent
    return obj


def box(bm, size, center, bevel=0.0):
    geom = bmesh.ops.create_cube(bm, size=1.0)
    verts = geom["verts"]
    bmesh.ops.scale(bm, vec=size, verts=verts)
    bmesh.ops.translate(bm, vec=center, verts=verts)
    if bevel:
        edges = list({e for v in verts for e in v.link_edges})
        bmesh.ops.bevel(bm, geom=edges, offset=bevel, segments=2, affect="EDGES")


def bar(bm, x0, x1, radius, segments=32, y=0.0, z=0.0):
    """Zylinder entlang x von x0 bis x1, in Hoehe y/z."""
    geom = bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=radius, radius2=radius,
                                 depth=x1 - x0)
    bmesh.ops.rotate(bm, verts=geom["verts"], cent=(0, 0, 0), matrix=Matrix.Rotation(math.pi / 2, 3, "Y"))
    bmesh.ops.translate(bm, verts=geom["verts"], vec=((x0 + x1) / 2, y, z))


def smooth(obj):
    obj.data.shade_smooth()
    return obj


class Builder:
    def __init__(self, scene):
        self.scene = scene
        self.materials = {key: material(key, color) for key, color in COLORS.items()}
        self.materials["metal"] = material("metal", COLORS["metal"], roughness=0.3)

    # --- Trainerin ---

    def character(self):
        """Importiert die Mixamo-Figur (mit ihren eigenen Materialien) und haengt das Skelett an die
        Gelenke."""
        if not os.path.exists(CHARACTER):
            raise SystemExit(f"Figur fehlt: {CHARACTER} (siehe Kopf dieser Datei)")
        before = set(bpy.data.objects)
        bpy.ops.import_scene.fbx(filepath=CHARACTER)
        new = set(bpy.data.objects) - before
        armature = next(o for o in new if o.type == "ARMATURE")
        self.armature = armature
        self.prefix = next(b.name for b in armature.data.bones if b.name.endswith("Hips"))[:-len("Hips")]
        for mat in {slot.material for obj in new for slot in obj.material_slots if slot.material}:
            matte(mat)
        bpy.context.view_layer.update()
        fuller([o for o in new if o.type == "MESH"], armature, self.prefix)
        for obj in new:
            obj.name = PREFIX + obj.name
            if obj.name.endswith("Shirt"):
                dark_jacket(obj)
            if obj.type == "MESH" and obj.name.endswith(("Body", "Shirt", "Pants")):
                # Die Haut glaetten, wo das Skelett sie staucht (Schultern, Achseln), und Kleidung mit ihr
                corrective = obj.modifiers.new("CorrectiveSmooth", "CORRECTIVE_SMOOTH")
                corrective.rest_source, corrective.smooth_type = "ORCO", "LENGTH_WEIGHTED"
                corrective.iterations, corrective.factor, corrective.use_pin_boundary = 10, 0.5, True
        self.rig()

        # Schuhsohlen auf die Matte
        set_pose(self.scene, {})
        depsgraph = bpy.context.evaluated_depsgraph_get()
        lowest = min((obj.matrix_world @ v.co).z for obj in new if obj.type == "MESH"
                     for v in obj.evaluated_get(depsgraph).data.vertices)
        armature.location.z += MAT_HEIGHT - lowest

    def bone(self, name):
        return self.armature.data.bones[self.prefix + name]

    def rig(self):
        """Gelenke als Empties (nur ihre Drehung zaehlt), darunter pro Knochen ein Ziel mit der
        Ruhelage des Knochens in Weltkoordinaten; der Knochen kopiert dessen Drehung."""
        root = self.joint("root", None)
        joints = {"root": root}
        for name, parent in JOINTS.items():
            joints[name] = self.joint(name, joints[parent])
            if name.split("_")[0] in TWIST_FIRST:
                joints[name].rotation_mode = "ZYX"
        armature = self.armature
        world = armature.matrix_world
        for joint, bones in BONES.items():
            for bone_name in bones:
                bone = self.bone(bone_name)
                rest = (world.to_3x3() @ bone.matrix_local.to_3x3()).normalized()
                correction = Matrix.Identity(3)
                if bone_name in HANGING:
                    direction = world @ bone.tail_local - world @ bone.head_local
                    correction = direction.rotation_difference(Vector((0, 0, -1))).to_matrix()
                target = bpy.data.objects.new(f"{PREFIX}target_{bone_name}", None)
                self.scene.collection.objects.link(target)
                target.parent = joints[joint]
                target.rotation_mode = "QUATERNION"
                target.rotation_quaternion = (correction @ rest).to_quaternion()
                target.empty_display_size = 0.02
                constraint = armature.pose.bones[bone.name].constraints.new("COPY_ROTATION")
                constraint.target = target
                constraint.owner_space = constraint.target_space = "WORLD"

    def joint(self, name, parent):
        obj = bpy.data.objects.new(PREFIX + name, None)
        self.scene.collection.objects.link(obj)
        obj.parent = parent
        obj.rotation_mode = "XYZ"
        obj.empty_display_size = 0.02
        obj["demo_joint"] = name
        return obj

    # --- Hanteln und Stab ---

    def props(self):
        """Hanteln in beiden Faeusten, der Stab von Faust zu Faust."""
        set_pose(self.scene, {}, fist=True)
        for s in ("Left", "Right"):
            bm = bmesh.new()
            bar(bm, -0.065, 0.065, 0.011)
            dumbbell = mesh_object(self.scene, f"dumbbell_bar_{s}", bm, self.materials["metal"])
            bm = bmesh.new()
            for x in (-0.095, 0.065):
                bar(bm, x, x + 0.03, 0.034, segments=8)
            weights = mesh_object(self.scene, f"dumbbell_weights_{s}", bm, self.materials["plate"], dumbbell)
            for obj in (dumbbell, weights):
                obj["demo_prop"] = "dumbbells"
            center, across, forearm = grip(self.armature, self.prefix, s)
            z = across.cross(forearm).normalized()
            attach(dumbbell, self.armature, f"{self.prefix}{s}Hand",
                   Matrix.Translation(center) @ Matrix((across, z.cross(across), z)).transposed().to_4x4())
            # Mitte der Faust, fuer den Stab
            point = bpy.data.objects.new(f"{PREFIX}grip_{s}", None)
            self.scene.collection.objects.link(point)
            point.empty_display_size = 0.03
            attach(point, self.armature, f"{self.prefix}{s}Hand", Matrix.Translation(center))

        # Der Stab liegt mitten zwischen den Faeusten und zeigt von der linken zur rechten. An einer Hand
        # festgemacht wuerde er sich mit ihr neigen, sobald sich das Handgelenk dreht
        bm = bmesh.new()
        bar(bm, -0.55, 0.55, 0.014)
        stick = mesh_object(self.scene, "stick", bm, self.materials["wood"])
        stick["demo_prop"] = "stick"
        grips = {s: self.scene.objects[f"{PREFIX}grip_{s}"] for s in ("Left", "Right")}
        stick.constraints.new("COPY_LOCATION").target = grips["Left"]
        halfway = stick.constraints.new("COPY_LOCATION")
        halfway.target, halfway.influence = grips["Right"], 0.5
        track = stick.constraints.new("TRACK_TO")
        track.target, track.track_axis, track.up_axis = grips["Right"], "TRACK_NEGATIVE_X", "UP_Z"

    # --- Raum ---

    def room(self):
        """Therapieraum aus einfachen Formen: Holzboden, Waende, Fenster, Sprossenwand, Pflanzen,
        Gymnastikball, Matte."""
        scene, m = self.scene, self.materials
        bm = bmesh.new()
        box(bm, (9, 9, 0.02), (0, -0.5, -0.01))
        mesh_object(scene, "floor", bm, self.floor_material())
        bm = bmesh.new()
        box(bm, (9, 0.1, 3.2), (0, 2.45, 1.6))
        mesh_object(scene, "wall_back", bm, m["wall"])
        bm = bmesh.new()
        for x in (-3.25, 3.25):
            box(bm, (0.1, 6, 3.2), (x, -0.6, 1.6))
        mesh_object(scene, "wall_side", bm, m["wall_side"])
        bm = bmesh.new()
        box(bm, (6.4, 0.03, 0.1), (0, 2.39, 0.05))
        for x in (-3.19, 3.19):
            box(bm, (0.03, 6, 0.1), (x, -0.6, 0.05))
        mesh_object(scene, "baseboard", bm, m["white"])

        # Fenster links hinten: Rahmen und Himmel
        bm = bmesh.new()
        x0, z0, w, h = -1.75, 1.05, 1.1, 1.25
        for dx in (-w / 2, 0, w / 2):
            box(bm, (0.06, 0.06, h + 0.06), (x0 + dx, 2.38, z0 + h / 2))
        for dz in (0, h / 2, h):
            box(bm, (w + 0.06, 0.06, 0.06), (x0, 2.38, z0 + dz))
        mesh_object(scene, "window_frame", bm, m["white"])
        bm = bmesh.new()
        box(bm, (w, 0.01, h), (x0, 2.395, z0 + h / 2))
        mesh_object(scene, "window_sky", bm, self.flat_material("sky_emit", COLORS["sky"]))

        # Sprossenwand rechts hinten
        bm = bmesh.new()
        for dx in (-0.45, 0.45):
            box(bm, (0.07, 0.07, 2.5), (1.75 + dx, 2.33, 1.25), bevel=0.01)
        for i in range(1, 14):
            bar(bm, 1.3, 2.2, 0.018, segments=12, y=2.33, z=0.17 * i)
        mesh_object(scene, "wallbars", bm, m["wood"])

        # Pflanzen und Gymnastikball
        for name, (x, y), size in (("plant_left", (-1.55, 1.75), 1.0), ("plant_right", (2.6, 1.9), 0.8)):
            bm = bmesh.new()
            bmesh.ops.create_cone(bm, cap_ends=True, segments=16, radius1=0.16 * size, radius2=0.22 * size,
                                  depth=0.4 * size, matrix=Matrix.Translation((x, y, 0.2 * size)))
            mesh_object(scene, name + "_pot", bm, m["pot"])
            bm = bmesh.new()
            for dx, dy, dz, r in ((0, 0, 0.75, 0.3), (-0.17, 0.05, 0.95, 0.22), (0.16, -0.05, 1.0, 0.24),
                                  (0.02, 0.02, 1.2, 0.2)):
                bmesh.ops.create_icosphere(bm, subdivisions=2, radius=r * size,
                                           matrix=Matrix.Translation((x + dx * size, y + dy * size, dz * size)))
            mesh_object(scene, name + "_leaves", bm, m["leaves"])
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=32, v_segments=16, radius=0.33,
                                  matrix=Matrix.Translation((1.35, 1.3, 0.33)))
        smooth(mesh_object(scene, "ball", bm, m["ball"]))

        bm = bmesh.new()
        box(bm, (0.62, 1.8, MAT_HEIGHT), (0, 0.2, MAT_HEIGHT / 2), bevel=0.005)
        mesh_object(scene, "mat", bm, m["mat"])

    def floor_material(self):
        """Holzdielen: Brick-Textur mit zwei Holztoenen."""
        mat = material("floor", COLORS["floor"], roughness=0.5)
        nt = mat.node_tree
        bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
        bricks = nt.nodes.new("ShaderNodeTexBrick")
        bricks.inputs["Color1"].default_value = (*COLORS["floor"], 1)
        bricks.inputs["Color2"].default_value = (*COLORS["floor_dark"], 1)
        bricks.inputs["Mortar"].default_value = (*[c * 0.6 for c in COLORS["floor_dark"]], 1)
        bricks.inputs["Scale"].default_value = 1.0
        bricks.inputs["Mortar Size"].default_value = 0.006
        bricks.inputs["Brick Width"].default_value = 1.2
        bricks.inputs["Row Height"].default_value = 0.18
        bricks.offset = 0.5
        coords = nt.nodes.new("ShaderNodeTexCoord")
        nt.links.new(coords.outputs["Object"], bricks.inputs["Vector"])
        nt.links.new(bricks.outputs["Color"], bsdf.inputs["Base Color"])
        return mat

    @staticmethod
    def flat_material(name, color):
        """Leuchtende Flaeche ohne Schattierung (Himmel im Fenster)."""
        mat = bpy.data.materials.new(PREFIX + name)
        nt = mat.node_tree
        nt.nodes.clear()
        emission = nt.nodes.new("ShaderNodeEmission")
        emission.inputs["Color"].default_value = (*color, 1.0)
        nt.links.new(emission.outputs[0], nt.nodes.new("ShaderNodeOutputMaterial").inputs["Surface"])
        return mat

    def lights(self):
        world = bpy.data.worlds.get(PREFIX + "world") or bpy.data.worlds.new(PREFIX + "world")
        world.use_nodes = True
        background = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
        background.inputs["Color"].default_value = (0.8, 0.85, 0.9, 1.0)
        background.inputs["Strength"].default_value = 0.4
        self.scene.world = world

        # weiches Licht von vorne links (key), schwaecher von vorne rechts (fill), etwas von hinten (rim)
        for name, location, energy, size in (("key", (-2.0, -2.6, 3.0), 150, 2.5),
                                             ("fill", (2.4, -2.4, 2.0), 60, 3.0),
                                             ("rim", (0.8, 1.5, 3.2), 40, 2.0)):
            light = bpy.data.lights.new(PREFIX + name, "AREA")
            light.energy, light.size = energy, size
            obj = bpy.data.objects.new(PREFIX + name, light)
            self.scene.collection.objects.link(obj)
            obj.location = location
            obj.rotation_euler = (Vector((0, 0, 1.0)) - Vector(location)).to_track_quat("-Z", "Y").to_euler()

    def camera(self):
        cam = bpy.data.cameras.new(PREFIX + "camera")
        cam.lens = 50
        obj = bpy.data.objects.new(PREFIX + "camera", cam)
        self.scene.collection.objects.link(obj)
        self.scene.camera = obj


def grip(armature, prefix, s):
    """Mitte der Faust, Achse quer durch die Faust (senkrecht zum Unterarm), Richtung des Unterarms."""
    def point(bone, end="head"):
        pose_bone = armature.pose.bones[prefix + bone]
        return armature.matrix_world @ (pose_bone.head if end == "head" else pose_bone.tail)
    forearm = (point(f"{s}Hand") - point(f"{s}ForeArm")).normalized()
    across = point(f"{s}HandIndex1") - point(f"{s}HandPinky1")
    across = (across - across.dot(forearm) * forearm).normalized()
    center = (point(f"{s}HandMiddle1") + point(f"{s}HandMiddle3", "tail")) / 2
    return center, across, forearm


def attach(obj, armature, bone, world):
    """Haengt obj an einen Knochen, ohne dass es sich in der aktuellen Pose bewegt."""
    obj.parent, obj.parent_type, obj.parent_bone = armature, "BONE", bone
    bpy.context.view_layer.update()
    obj.matrix_world = world


def armature_of(scene):
    return next(o for o in scene.objects if o.type == "ARMATURE")


def set_pose(scene, pose, fist=False):
    """Stellt die Gelenke ohne Keyframes auf pose (ueber REST) und die Finger zur Faust oder locker."""
    pose = full_pose(pose)
    for name, obj in joints(scene).items():
        obj.rotation_euler = [math.radians(a) for a in pose.get(name, (0, 0, 0))]
    set_fingers(scene, fist)
    bpy.context.view_layer.update()


def set_fingers(scene, fist):
    """Faust (mit Hanteln oder Stab) oder locker leicht gebeugt. Die Fingerknochen beugen um ihre
    lokale x-Achse; der Daumen (1. Glied) bleibt."""
    for pose_bone in armature_of(scene).pose.bones:
        finger = pose_bone.name.split(":")[-1].replace("Left", "").replace("Right", "")
        if not finger.startswith("Hand") or not finger[4:].startswith(FINGERS) or finger.endswith("4"):
            continue
        pose_bone.rotation_mode = "XYZ"
        if "Thumb" in finger:
            angle = 0 if finger.endswith("1") else (35 if fist else 8)
        else:
            angle = 80 if fist else 8
        pose_bone.rotation_euler = (math.radians(angle), 0, 0)


def build_scene():
    """Baut die Szene SCENE neu auf (eine vorhandene wird ersetzt) und gibt sie zurueck. Im
    Hintergrund (-b) ohne Fenster wird die leere Startszene benutzt."""
    window = bpy.context.window
    if window:
        old = bpy.data.scenes.get(SCENE)
        if old:
            for obj in list(old.objects):
                bpy.data.objects.remove(obj)
            bpy.data.scenes.remove(old)
        scene = bpy.data.scenes.new(SCENE)
        window.scene = scene        # der FBX-Import legt seine Objekte in die aktive Szene
    else:
        scene = bpy.context.scene
        for obj in list(scene.objects):
            bpy.data.objects.remove(obj)
        scene.name = SCENE
    for mesh in [m for m in bpy.data.meshes if m.users == 0]:
        bpy.data.meshes.remove(mesh)

    builder = Builder(scene)
    builder.character()
    builder.props()
    builder.room()
    builder.lights()
    builder.camera()
    setup_render(scene)
    return scene


def setup_render(scene):
    render = scene.render
    try:
        render.engine = "BLENDER_EEVEE"
    except TypeError as e:
        print(f"EEVEE nicht verfuegbar ({e}), bleibe bei {render.engine}")
    if hasattr(scene, "eevee"):
        scene.eevee.taa_render_samples = 16    # weiche Schatten auf Haut und Haar
    render.resolution_x, render.resolution_y = RESOLUTION
    render.resolution_percentage = 100
    render.fps, render.fps_base = FPS, 1.0
    try:
        scene.view_settings.view_transform = "AgX"        # Glanzlichter auf Haut und Shirt ohne Ausbrennen
    except TypeError:
        pass

    settings = render.image_settings
    if "media_type" in settings.bl_rna.properties:        # ab Blender 5: Bild oder Video
        settings.media_type = "VIDEO"
    settings.file_format = "FFMPEG"
    render.ffmpeg.format = "MPEG4"
    render.ffmpeg.codec = "H264"
    render.ffmpeg.constant_rate_factor = "HIGH"      # MEDIUM verwischt Gesicht und Kanten
    render.ffmpeg.ffmpeg_preset = "GOOD"
    render.ffmpeg.audio_codec = "NONE"


def joints(scene):
    return {obj["demo_joint"]: obj for obj in scene.objects if "demo_joint" in obj}


def fit_camera(scene, view):
    """Stellt die Kamera (Richtung aus VIEWS) so weit weg, dass die Figur mit Haaren, Haenden, Fuessen und
    Props in jedem Frame der Uebung ganz im Bild ist, mit MARGIN Rand."""
    azimuth, tilt = VIEWS[view]
    camera = scene.camera
    camera.rotation_euler = (math.radians(90 - tilt), 0, math.radians(azimuth))
    bpy.context.view_layer.update()
    axes = camera.matrix_world.to_3x3()
    # Pro Frame und Objekt nur die aeussersten Punkte in Bildrichtung (rechts, oben, diagonal)
    directions = [axes @ Vector(d) for d in ((1, 0, 0), (0, 1, 0), (1, 1, 0), (1, -1, 0))]
    objects = [o for o in scene.objects if o.type == "MESH" and not o.hide_render
               and (o.find_armature() or "demo_prop" in o)]
    points = []
    for frame in range(scene.frame_start, scene.frame_end + 1, 3):
        scene.frame_set(frame)
        depsgraph = bpy.context.evaluated_depsgraph_get()
        for obj in objects:
            evaluated = obj.evaluated_get(depsgraph)
            world = evaluated.matrix_world
            verts = [world @ v.co for v in evaluated.data.vertices]
            for d in directions:
                points.append(max(verts, key=d.dot))
                points.append(min(verts, key=d.dot))
    scene.frame_set(scene.frame_start)
    center = sum(points, Vector()) / len(points)
    grow = 1 / (1 - 2 * MARGIN)
    coords = [c for p in points for c in center + (p - center) * grow]
    depsgraph = bpy.context.evaluated_depsgraph_get()
    location, _ = camera.evaluated_get(depsgraph).camera_fit_coords(depsgraph, coords)
    camera.location = location


def animate(scene, name):
    """Setzt die Keyframes der Uebung, blendet ihre Props ein und stellt die Kamera. Gibt die Zahl
    der Frames zurueck."""
    exercise = EXERCISES[name]
    rig = joints(scene)
    for obj in rig.values():
        obj.animation_data_clear()
    set_fingers(scene, exercise.get("fist", False))

    base = exercise.get("base", {})
    last = 1
    for t, pose in exercise["keys"]:
        frame = 1 + round(t * FPS)
        for joint, rotation in full_pose({**base, **pose}).items():
            rig[joint].rotation_euler = [math.radians(a) for a in rotation]
            rig[joint].keyframe_insert("rotation_euler", frame=frame)
        last = frame

    # Der letzte Keyframe gleicht dem ersten: ohne ihn wiederholt das Video nahtlos
    scene.frame_start, scene.frame_end = 1, last - 1
    scene.frame_set(1)

    props = exercise.get("props", [])
    for obj in scene.objects:
        if "demo_prop" in obj:
            obj.hide_render = obj.hide_viewport = obj["demo_prop"] not in props
    fit_camera(scene, exercise.get("view", "full"))
    return scene.frame_end


def render(scene, name):
    os.makedirs(DEMO_DIR, exist_ok=True)
    frames = animate(scene, name)
    path = os.path.join(DEMO_DIR, f"{name}.mp4")
    scene.render.filepath = path
    bpy.ops.render.render(animation=True, scene=scene.name)
    print(f"{os.path.relpath(path, APP_DIR)}: {frames} Frames, {frames / FPS:.1f} s")


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    names = argv or list(EXERCISES)
    unknown = [n for n in names if n not in EXERCISES]
    if unknown:
        raise SystemExit(f"Unbekannte Uebung: {', '.join(unknown)} (bekannt: {', '.join(EXERCISES)})")
    scene = build_scene()
    for name in names:
        render(scene, name)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(DEMO_DIR, "demos.blend"), copy=True)


if __name__ == "__main__":
    main()
