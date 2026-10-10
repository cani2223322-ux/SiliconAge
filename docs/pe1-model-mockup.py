"""PE-1 3D mock-ups: primitives -> triangles -> a small z-buffer renderer (Lambert + glow + outline)."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ---------------------------------------------------------------- mesh building
class Mesh:
    def __init__(self):
        self.tris = []          # (v0, v1, v2, color, emissive, part id)
        self.pid = 0

    def part(self):
        self.pid += 1
        return self.pid

    def quad(self, a, b, c, d, col, em=False, pid=None):
        pid = pid or self.pid
        self.tris.append((a, b, c, col, em, pid))
        self.tris.append((a, c, d, col, em, pid))

    def box(self, x0, y0, z0, x1, y1, z1, col, em=False, bevel=0.0):
        pid = self.part()
        p = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
        P = lambda i, j, k: np.array([(x0, x1)[i], (y0, y1)[j], (z0, z1)[k]], float)
        faces = [((0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)), ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)),
                 ((0, 0, 0), (0, 0, 1), (1, 0, 1), (1, 0, 0)), ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
                 ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)), ((0, 0, 1), (0, 1, 1), (1, 1, 1), (1, 0, 1))]
        for f in faces:
            self.quad(*[P(*v) for v in f], col, em, pid)

    def cyl_x(self, x0, x1, cy, cz, r, col, em=False, n=16, r1=None, cap=True):
        """Cylinder (or cone when r1 is given) along x."""
        pid = self.part()
        r1 = r if r1 is None else r1
        for i in range(n):
            a0, a1 = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
            p = lambda x, rr, a: np.array([x, cy + rr * math.cos(a), cz + rr * math.sin(a)])
            self.quad(p(x0, r, a0), p(x1, r1, a0), p(x1, r1, a1), p(x0, r, a1), col, em, pid)
            if cap:
                c0, c1 = np.array([x0, cy, cz]), np.array([x1, cy, cz])
                self.tris.append((c0, p(x0, r, a1), p(x0, r, a0), col, em, pid))
                self.tris.append((c1, p(x1, r1, a0), p(x1, r1, a1), col, em, pid))

    def torus_x(self, x, cy, cz, R, r, col, em=False, n=24, m=10):
        pid = self.part()
        def p(u, v):
            return np.array([x + r * math.sin(v), cy + (R + r * math.cos(v)) * math.cos(u),
                             cz + (R + r * math.cos(v)) * math.sin(u)])
        for i in range(n):
            for j in range(m):
                u0, u1 = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
                v0, v1 = 2 * math.pi * j / m, 2 * math.pi * (j + 1) / m
                self.quad(p(u0, v0), p(u1, v0), p(u1, v1), p(u0, v1), col, em, pid)

    def sphere(self, c, r, col, em=False, n=16, m=10):
        pid = self.part()
        c = np.array(c, float)
        def p(u, v):
            return c + r * np.array([math.cos(v) * math.cos(u), math.sin(v), math.cos(v) * math.sin(u)])
        for i in range(n):
            for j in range(m):
                u0, u1 = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
                v0, v1 = -math.pi / 2 + math.pi * j / m, -math.pi / 2 + math.pi * (j + 1) / m
                self.quad(p(u0, v0), p(u1, v0), p(u1, v1), p(u0, v1), col, em, pid)


# ---------------------------------------------------------------- rendering
def render(mesh, size=512, yaw=-32, pitch=24, roll=0, scale=1.0, light=(-0.4, 0.8, 0.6), center=None):
    tris = mesh.tris
    V = np.array([[t[0], t[1], t[2]] for t in tris])               # (T, 3, 3)
    if center is None:
        center = (V.reshape(-1, 3).max(0) + V.reshape(-1, 3).min(0)) / 2
    V = V - center
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    cr, sr = math.cos(math.radians(roll)), math.sin(math.radians(roll))
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    Rz = np.array([[cr, -sr, 0], [sr, cr, 0], [0, 0, 1]])
    R = Rx @ Ry @ Rz
    W = V @ R.T
    ext = np.abs(W[..., :2]).max()
    k = size * 0.44 / ext * scale
    S = np.empty_like(W)
    S[..., 0] = size / 2 + W[..., 0] * k
    S[..., 1] = size / 2 - W[..., 1] * k
    S[..., 2] = W[..., 2]
    L = np.array(light, float); L /= np.linalg.norm(L)
    color = np.zeros((size, size, 3)); depth = np.full((size, size), -1e9)
    pidb = np.zeros((size, size), int); emb = np.zeros((size, size))
    for ti, t in enumerate(tris):
        a, b, c = S[ti]
        n = np.cross(W[ti][1] - W[ti][0], W[ti][2] - W[ti][0])
        nn = np.linalg.norm(n)
        if nn == 0:
            continue
        n /= nn
        if n[2] < 0:
            n = -n
        if t[4]:
            shade = np.array(t[3], float)
        else:
            lam = max(0.0, n @ L)
            rim = (1 - abs(n[2])) ** 3 * 0.25
            spec = max(0.0, (n @ (L + np.array([0, 0, 1])) / np.linalg.norm(L + np.array([0, 0, 1])))) ** 24 * 0.55
            shade = np.array(t[3], float) * (0.38 + 0.7 * lam) + 255 * (spec + rim)
        x0, x1 = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(size - 1, math.ceil(max(a[0], b[0], c[0]))))
        y0, y1 = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(size - 1, math.ceil(max(a[1], b[1], c[1]))))
        if x1 < x0 or y1 < y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(d) < 1e-9:
            continue
        w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
        w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        z = w0 * a[2] + w1 * b[2] + w2 * c[2]
        sub = depth[y0:y1 + 1, x0:x1 + 1]
        m = inside & (z > sub)
        sub[m] = z[m]
        color[y0:y1 + 1, x0:x1 + 1][m] = np.clip(shade, 0, 255)
        pidb[y0:y1 + 1, x0:x1 + 1][m] = t[5]
        emb[y0:y1 + 1, x0:x1 + 1][m] = 1.0 if t[4] else 0.0
    mask = pidb > 0
    # outline: object edge (thick) + part edges (thin)
    edge = np.zeros_like(mask)
    sh = lambda a, dx, dy: np.roll(np.roll(a, dy, 0), dx, 1)
    t = max(2, size // 128)
    for dx in range(-t, t + 1):
        for dy in range(-t, t + 1):
            edge |= sh(mask, dx, dy)
    outer = edge & ~mask
    inner = np.zeros_like(mask)
    for dx, dy in ((1, 0), (0, 1)):
        q = sh(pidb, dx, dy)
        qd = sh(depth, dx, dy)
        inner |= mask & (q != pidb) & (np.abs(qd - depth) > 0.04) & (q > 0)
    img = np.zeros((size, size, 4))
    img[mask, :3] = color[mask]
    img[mask, 3] = 255
    img[inner, :3] *= 0.55
    img[outer] = (33, 36, 41, 255)
    out = Image.fromarray(img.astype(np.uint8), "RGBA")
    # glow of emissive parts
    em = Image.fromarray((np.dstack([color * emb[..., None], emb * 255])).astype(np.uint8), "RGBA")
    glow = em.filter(ImageFilter.GaussianBlur(size / 40))
    g = np.array(glow).astype(float)
    base = np.array(out).astype(float)
    a = g[..., 3:4] / 255 * 0.9
    base[..., :3] = base[..., :3] * (1 - a) + g[..., :3] * a
    base[..., 3] = np.maximum(base[..., 3], g[..., 3] * 0.8)
    return Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- palette and models
STEEL = (178, 186, 198); STEEL_D = (120, 127, 140); GUN = (78, 83, 94); GUN_D = (56, 60, 68)
COP = (214, 128, 64); BRASS = (224, 186, 92); WRAP = (66, 60, 56); GLASSC = (150, 180, 196)
MODES = {"Точка": (110, 215, 255), "Импульс": (186, 132, 255), "Сифон": (130, 236, 100)}


def grip(m, x=1.0):
    m.box(x, -3.4, -0.55, x + 1.3, 0, 0.55, WRAP)
    for y in (-0.8, -1.6, -2.4):
        m.box(x - 0.05, y - 0.15, -0.6, x + 1.35, y + 0.15, 0.6, (50, 46, 43))
    m.box(x + 1.3, -1.6, -0.25, x + 2.6, -1.4, 0.25, GUN_D)          # guard
    m.box(x + 2.4, -1.6, -0.25, x + 2.6, -0.2, 0.25, GUN_D)
    m.box(x + 1.6, -1.0, -0.12, x + 1.8, -0.1, 0.12, BRASS)          # trigger


def m_tesla(mode):
    c = MODES[mode]; m = Mesh()
    m.box(0, 0, -0.9, 7.5, 1.8, 0.9, STEEL)                          # body
    m.box(2.6, 0.25, -0.95, 6.4, 1.55, 0.95, GUN_D)                   # coil window
    for i in range(9):
        m.cyl_x(2.8 + i * 0.4, 3.0 + i * 0.4, 0.9, 0, 0.99, COP, n=16)  # coil turns showing through it
    m.box(7.5, -0.2, -1.1, 9.0, 2.0, 1.1, GUN)                        # front block
    m.cyl_x(2.6, 6.6, 2.75, 0, 0.55, GLASSC, n=16)                    # valve glass
    m.cyl_x(2.9, 6.3, 2.75, 0, 0.25, c, em=True, n=10)                # filament
    m.cyl_x(2.3, 2.7, 2.75, 0, 0.65, BRASS); m.cyl_x(6.5, 6.9, 2.75, 0, 0.65, BRASS)
    m.box(4.5, 1.8, -0.15, 4.8, 2.3, 0.15, GUN)
    m.torus_x(9.6, 0.9, 0, 1.55, 0.22, COP)                           # emitter ring
    m.cyl_x(9.0, 10.2, 0.9, 0, 0.35, GUN)
    m.sphere((10.9, 0.9, 0), 0.75, c, em=True)
    grip(m)
    return m


def m_rings(mode):
    c = MODES[mode]; m = Mesh()
    m.box(0, 0, -0.95, 6.8, 2.0, 0.95, STEEL)
    for i in range(6):
        m.box(1.0 + i * 0.85, 2.0, -0.6, 1.4 + i * 0.85, 2.35, 0.6, GUN)   # top vents / fins
    m.cyl_x(6.8, 13.0, 1.0, 0, 0.42, GUN_D, n=12)                       # barrel
    m.cyl_x(6.8, 13.4, 1.0, 0, 0.16, c, em=True, n=8)                   # core
    for x, R, col in ((7.6, 1.55, COP), (9.4, 1.3, STEEL), (11.2, 1.05, COP)):
        m.torus_x(x, 1.0, 0, R, 0.24, col)
        m.torus_x(x, 1.0, 0, R - 0.32, 0.07, c, em=True, n=20, m=6)
    m.cyl_x(2.8, 6.3, -0.55, 0, 0.42, GUN)                              # battery under
    for i in range(3):
        m.cyl_x(3.2 + i * 1.0, 3.6 + i * 1.0, -0.55, 0, 0.44, c, em=True, n=10)
    grip(m)
    return m


def m_lens(mode):
    c = MODES[mode]; m = Mesh()
    m.box(0, 0, -0.9, 6.0, 2.1, 0.9, STEEL)
    m.box(6.0, 0.3, -0.85, 8.0, 1.8, 0.85, STEEL_D)                     # sloped nose (stepped)
    for i in range(5):
        m.box(1.0 + i * 0.9, 2.1, -0.75, 1.35 + i * 0.9, 2.75, 0.75, GUN)   # fins
    m.box(1.2, 0.6, 0.9, 4.2, 1.5, 0.98, GUN_D)                         # side screen
    m.box(1.4, 0.8, 0.97, 3.0, 1.3, 1.0, c, em=True)
    m.cyl_x(8.0, 8.9, 1.05, 0, 1.15, GUN, n=24)                         # lens housing
    m.cyl_x(8.9, 9.3, 1.05, 0, 0.95, c, em=True, n=24, r1=0.8)          # the lens
    m.torus_x(8.9, 1.05, 0, 1.05, 0.12, BRASS)
    grip(m)
    return m


def m_capsule(mode):
    c = MODES[mode]; m = Mesh()
    m.box(0, 0, -0.9, 8.5, 1.8, 0.9, STEEL)
    m.cyl_x(2.85, 5.55, 1.0, 0, 1.1, c, em=True, n=20)                  # the energy capsule (seen through the cage)
    for k in range(6):                                                  # the cage bars of the glass
        a = 2 * math.pi * (k + 0.5) / 6
        m.cyl_x(2.8, 5.6, 1.0 + 1.22 * math.cos(a), 1.22 * math.sin(a), 0.1, STEEL_D, n=6)
    m.cyl_x(2.5, 2.85, 1.0, 0, 1.4, BRASS, n=20); m.cyl_x(5.55, 5.9, 1.0, 0, 1.4, BRASS, n=20)
    for p in ((3.4, -0.1, 0.95), (4.6, -0.3, 0.9), (4.0, 0.1, 0.98)):
        m.sphere(p, 0.12, (240, 255, 240), em=True, n=6, m=4)
    m.cyl_x(8.5, 10.2, 0.9, 0, 0.45, GUN_D)                             # needle base
    m.cyl_x(10.2, 11.6, 0.9, 0, 0.18, c, em=True, n=8, r1=0.05)
    for s in (1, -1):                                                   # claws
        m.box(9.6, 0.9 + s * 0.6, -0.2, 10.4, 0.9 + s * 1.3, 0.2, STEEL)
        m.box(10.4, 0.9 + s * 1.3, -0.2, 11.4, 0.9 + s * 1.6, 0.2, STEEL)
        m.box(11.2, 0.9 + s * 0.9, -0.2, 11.6, 0.9 + s * 1.6, 0.2, STEEL)
    m.cyl_x(3.0, 2.0, -1.0, 0.5, 0.12, COP, n=6)                        # cable
    grip(m)
    return m


def m_rail(mode):
    """New: Рельсовый разрядник — two long rails with arcs between, a capacitor bank on the side."""
    c = MODES[mode]; m = Mesh()
    m.box(0, 0, -1.0, 6.0, 2.0, 1.0, GUN)
    m.box(0.3, 0.3, -1.05, 5.7, 1.7, 1.05, STEEL)
    for s in (1, -1):
        m.box(5.5, 0.3 + (s > 0) * 1.0, -0.7, 13.0, 0.7 + (s > 0) * 1.0, 0.7, STEEL_D)
    for i in range(7):
        x = 6.5 + i * 0.95
        m.box(x, 0.7, -0.08, x + 0.12, 1.3, 0.08, c, em=True)            # arcs between the rails
    for i in range(3):
        m.cyl_x(0.8 + i * 1.6, 2.0 + i * 1.6, 1.0, 1.35, 0.42, COP)      # capacitor bank on the side
        m.cyl_x(2.0 + i * 1.6, 2.15 + i * 1.6, 1.0, 1.35, 0.44, c, em=True, n=10)
    m.box(1.0, 2.0, -0.4, 5.0, 2.5, 0.4, GUN_D)                          # sight rail
    m.box(4.2, 2.5, -0.25, 4.8, 2.9, 0.25, c, em=True)
    grip(m)
    return m


def m_crown(mode):
    """New: Корона-излучатель — a short heavy body and a crown of six emitter spikes around a glowing core."""
    c = MODES[mode]; m = Mesh()
    m.cyl_x(0, 6.5, 1.0, 0, 1.15, STEEL, n=20)
    m.cyl_x(6.5, 7.6, 1.0, 0, 1.35, GUN, n=20)
    for i in range(4):
        m.cyl_x(1.0 + i * 1.3, 1.25 + i * 1.3, 1.0, 0, 1.2, GUN_D, n=20)  # bands
    m.cyl_x(7.6, 8.4, 1.0, 0, 0.7, c, em=True, n=16)                     # core
    for k in range(6):
        a = 2 * math.pi * k / 6
        cy, cz = 1.0 + 1.0 * math.cos(a), 1.0 * math.sin(a)
        m.cyl_x(7.6, 10.0, cy, cz, 0.22, COP if k % 2 else STEEL, n=8, r1=0.04)
    m.sphere((9.3, 1.0, 0), 0.4, c, em=True)
    m.box(1.5, 2.1, -0.3, 4.5, 2.6, 0.3, GUN_D)
    grip(m, 1.2)
    return m


ROWS = [("Г. «Тесла-разрядник»", m_tesla), ("Ж. «Сифон-капсула»", m_capsule), ("Д. «Кольцевой проектор»", m_rings),
        ("Е. «Линза-глушитель»", m_lens), ("З. «Рельсовый разрядник» (новый)", m_rail), ("И. «Корона-излучатель» (новый)", m_crown)]


def sheet(path):
    font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 22)
    small = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 16)
    T = 300
    cols = ["Точка", "Импульс", "Сифон"]
    W = 260 + 3 * (T + 20) + 260
    H = 50 + len(ROWS) * (T + 20)
    img = Image.new("RGBA", (W, H), (44, 46, 54, 255))
    d = ImageDraw.Draw(img)
    for i, mo in enumerate(cols):
        d.text((260 + i * (T + 20) + 8, 14), "режим: " + mo, font=font, fill=(220, 224, 232))
    d.text((260 + 3 * (T + 20) + 6, 14), "в слоте / в руке", font=font, fill=(220, 224, 232))
    for r, (name, fn) in enumerate(ROWS):
        y = 50 + r * (T + 20)
        a, b = name.split(" ", 1)
        d.text((14, y + T // 2 - 30), a, font=font, fill=(240, 240, 245))
        d.text((14, y + T // 2 - 2), b, font=small if len(b) > 22 else font, fill=(240, 240, 245))
        for i, mo in enumerate(cols):
            x = 260 + i * (T + 20)
            d.rectangle((x, y, x + T, y + T), fill=(58, 61, 70))
            im = render(fn(mo), size=T)
            img.paste(im, (x, y), im)
        x = 260 + 3 * (T + 20) + 10
        icon = render(fn("Импульс"), size=128)
        for k, sc in enumerate((32, 64)):
            sx, sy = x + k * 52, y + 30
            d.rectangle((sx, sy, sx + sc + 4, sy + sc + 4), fill=(139, 139, 139))
            sm = icon.resize((sc, sc), Image.LANCZOS)
            img.paste(sm, (sx + 2, sy + 2), sm)
        hand = render(fn("Импульс"), size=150, yaw=-70, pitch=12, roll=-8)
        img.paste(hand, (x, y + 120), hand)
    img.save(path)


if __name__ == "__main__":
    sheet("pe1_mockup3d.png")
