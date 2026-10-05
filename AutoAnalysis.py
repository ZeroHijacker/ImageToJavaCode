"""Extract data-driven Java 2D shape proposals from a photograph.

OpenCV supplies color clustering, contours, ellipse fitting and edge detection.
Least-squares Bezier/circular-arc fits are computed with NumPy. Java evaluates
the proposals against its own antialiased renderer before accepting them.
"""
import argparse
import json
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps, ImageFilter


STYLES = ("original", "realism", "mosaic", "abstract", "cubism", "minimalist", "pixelated")
FILTERS = ("none", "monochrome", "vintage", "sepia", "cool", "warm", "posterized")

def apply_style(image, style="original", recolor="none", strength=60):
    """Deterministic, non-neural filters; geometry is later reconstructed in Java."""
    if style not in STYLES or recolor not in FILTERS or not 0 <= strength <= 100:
        raise ValueError("Unknown style/filter or invalid strength")
    image = image.convert("RGB")
    if strength == 0:
        return image.copy()
    amount = strength / 100
    w, h = image.size
    rgb = np.asarray(image).copy()
    styled = image.copy()
    if style == "realism":
        styled = image.filter(ImageFilter.UnsharpMask(radius=1.2, percent=int(30+70*amount), threshold=3))
    elif style in ("mosaic", "pixelated"):
        cell = max(2, round(max(w,h)*(0.012+amount*0.045)))
        data = rgb.copy()
        for y in range(0,h,cell):
            for x in range(0,w,cell):
                tile=rgb[y:y+cell,x:x+cell]
                data[y:y+cell,x:x+cell]=np.round(tile.mean(axis=(0,1))).astype(np.uint8)
                if style == "mosaic":
                    data[y:min(y+1,h),x:x+cell]=[38,34,32]
                    data[y:y+cell,x:min(x+1,w)]=[38,34,32]
        styled=Image.fromarray(data)
    elif style == "minimalist":
        smooth=cv2.bilateralFilter(rgb,9,90,9)
        styled=Image.fromarray(smooth).quantize(colors=max(3,round(10-7*amount)),dither=Image.Dither.NONE).convert("RGB")
    elif style == "abstract":
        if min(w,h)>=8:
            bgr=cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR)
            data=cv2.stylization(bgr,sigma_s=30+100*amount,sigma_r=.3+.3*amount)
            styled=Image.fromarray(cv2.cvtColor(data,cv2.COLOR_BGR2RGB))
        styled=styled.quantize(colors=max(4,round(18-12*amount)),dither=Image.Dither.NONE).convert("RGB")
    elif style == "cubism":
        # Flat triangular planes on a deterministically jittered grid.
        cell=max(4,round(max(w,h)*(.04+.10*amount)))
        xs=list(range(0,w,cell))+[w-1];ys=list(range(0,h,cell))+[h-1]
        rng=np.random.default_rng(26115)
        points=np.array([[[x,y] for x in xs] for y in ys],dtype=np.int32)
        for iy in range(1,len(ys)-1):
            for ix in range(1,len(xs)-1):
                points[iy,ix]+=rng.integers(-cell//4,cell//4+1,2)
        data=rgb.copy()
        for iy in range(len(ys)-1):
            for ix in range(len(xs)-1):
                a,b,c,d=points[iy,ix],points[iy,ix+1],points[iy+1,ix+1],points[iy+1,ix]
                for tri in (np.array([a,b,c]),np.array([a,c,d])):
                    bx,by,bw,bh=cv2.boundingRect(tri)
                    bx=max(0,bx);by=max(0,by);bw=min(bw,w-bx);bh=min(bh,h-by)
                    if bw<1 or bh<1: continue
                    mask=np.zeros((bh,bw),np.uint8)
                    cv2.fillConvexPoly(mask,tri-[bx,by],255)
                    pixels=rgb[by:by+bh,bx:bx+bw][mask>0]
                    if len(pixels):
                        color=tuple(int(v) for v in pixels.mean(axis=0))
                        cv2.fillConvexPoly(data,tri,color)
        styled=Image.fromarray(data)
    gray=ImageOps.grayscale(styled)
    if recolor == "monochrome":
        styled=gray.convert("RGB")
    elif recolor == "sepia":
        styled=ImageOps.colorize(gray,"#28170f","#f5dfad")
    elif recolor == "vintage":
        tinted=ImageOps.colorize(gray,"#433b45","#f8e2b0")
        styled=Image.blend(styled,tinted,.55)
    elif recolor in ("cool", "warm"):
        data=np.asarray(styled,dtype=np.float32)
        data += np.array([-12,2,16] if recolor=="cool" else [16,3,-12])*amount
        styled=Image.fromarray(np.clip(data,0,255).astype(np.uint8))
    elif recolor == "posterized":
        styled=ImageOps.posterize(styled,3)
    return styled


def analyze(source, directory, quality, style="original", recolor="none", strength=60):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    maximum = (560, 800, 1000)[quality]
    with Image.open(source) as image:
        image = ImageOps.exif_transpose(image).convert("RGBA")
        image.thumbnail((maximum, maximum), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", image.size, "white")
        canvas.alpha_composite(image)
        image = canvas.convert("RGB")
    image.save(directory / "original.png")
    image=apply_style(image,style,recolor,strength)
    image.save(directory / "reference.png")
    rgb = np.asarray(image).copy()
    height, width = rgb.shape[:2]
    cv2.setRNGSeed(26115)
    cv2.setNumThreads(1)
    smooth = cv2.bilateralFilter(rgb, 5, 22, 4)
    # Learn a color palette from a bounded sample, then label the whole image.
    pixels = smooth.reshape(-1, 3).astype(np.float32)
    sample = pixels[::max(1, len(pixels) // 18000)]
    colors = min((16, 28, 40)[quality], len(np.unique(sample, axis=0)))
    colors = max(1, colors)
    _, _, palette = cv2.kmeans(sample, colors, None,
        (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 25, .35),
        1, cv2.KMEANS_PP_CENTERS)
    labels = np.empty(len(pixels), dtype=np.int32)
    for start in range(0, len(pixels), 12000):
        block = pixels[start:start + 12000]
        labels[start:start + len(block)] = np.argmin(
            np.sum((block[:, None] - palette[None]) ** 2, axis=2), axis=1)
    labels = labels.reshape(height, width)
    proposals = []
    counts = [0] * 10

    def emit(kind, coordinates, name, stroke=1.3, filled=True, angle=0):
        p = np.asarray(coordinates, dtype=float).reshape(-1)
        if not np.all(np.isfinite(p)) or np.max(np.abs(p)) > 100000:
            return
        if len(p) > 1800:
            return
        row = [str(kind), "0", str(round(stroke, 3)), str(filled).lower(), str(round(angle, 3))]
        row += [str(round(float(v), 3)) for v in p]
        proposals.append(",".join(row) + "\t" + name)
        counts[kind] += 1

    def polygon(contour):
        epsilon = (.8, .65, .5)[quality]
        approx = cv2.approxPolyDP(contour, epsilon, True).reshape(-1, 2)
        if len(approx) > 160:
            approx = cv2.approxPolyDP(contour, 1.4, True).reshape(-1, 2)
        return approx[:300]

    regions = []
    for color_id in range(colors):
        mask = np.uint8(labels == color_id) * 255
        contours, hierarchy = cv2.findContours(mask, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
        if hierarchy is None:
            continue
        for i, contour in enumerate(contours):
            if hierarchy[0, i, 3] != -1 or cv2.contourArea(contour) < 9:
                continue
            outer = polygon(contour)
            if len(outer) < 3:
                continue
            holes = []
            child = hierarchy[0, i, 2]
            while child != -1:
                hole = polygon(contours[child])
                if len(hole) >= 3 and cv2.contourArea(contours[child]) >= 5:
                    holes.append(hole)
                child = hierarchy[0, child, 0]
            regions.append((cv2.contourArea(contour), contour, outer, holes))
    regions.sort(key=lambda r: -r[0])
    for index, (_, contour, outer, holes) in enumerate(regions[:(600, 1200, 1800)[quality]]):
        name = f"Extracted color-region boundary {index + 1}"
        if holes:
            # Special Area record: sentinel, ring count, then vertex-count + xy per ring.
            rings = [outer] + holes
            data = [-999, len(rings)]
            for ring in rings:
                data.extend([len(ring), *ring.reshape(-1)])
            emit(9, data, name + " with subtracted holes")
        else:
            emit(8, outer, name)
        (cx, cy), (rw, rh), rotation = cv2.minAreaRect(contour)
        if min(rw, rh) >= 2:
            kind = 7 if abs(rw - rh) < min(rw, rh) * .12 else 6
            if kind == 7:
                rw = rh = (rw + rh) / 2
            emit(kind, [cx-rw/2, cy-rh/2, rw, rh], name + " fitted box", angle=rotation)
        if len(contour) >= 5:
            (cx, cy), (ew, eh), rotation = cv2.fitEllipse(contour)
            if min(ew, eh) >= 2 and max(ew, eh) < max(width, height) * 1.5:
                emit(4, [cx-ew/2, cy-eh/2, ew, eh], name + " fitted ellipse", angle=rotation)
                if max(ew, eh) / min(ew, eh) < 1.5:
                    diameter = (ew+eh)/2
                    emit(5, [cx-diameter/2, cy-diameter/2, diameter, diameter], name + " fitted circle")

    gray = cv2.cvtColor(smooth, cv2.COLOR_RGB2GRAY)
    edges = cv2.Canny(gray, 35, 100)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE)
    contours = sorted(contours, key=lambda c: -cv2.arcLength(c, False))
    segments = 0
    for contour in contours[:(250, 500, 750)[quality]]:
        pts = contour.reshape(-1, 2).astype(float)
        # Local connected edge samples are fitted, not generic face templates.
        for start in range(0, len(pts) - 9, 14):
            q = pts[start:start + 30]
            length = np.linalg.norm(np.diff(q, axis=0), axis=1)
            if len(q) < 10 or sum(length) < 9 or np.linalg.norm(q[-1]-q[0]) < 4:
                continue
            t = np.r_[0, np.cumsum(length)] / sum(length)
            begin, end = q[0], q[-1]
            name = f"Extracted edge segment {segments + 1}"
            line = begin[None]*(1-t[:, None]) + end[None]*t[:, None]
            if np.mean(np.sum((line-q)**2, axis=1)) < 3:
                emit(0, [*begin, *end], name + " line", filled=False)
            basis = 2*(1-t)*t
            residual = q - (1-t[:, None])**2*begin - t[:, None]**2*end
            control = np.sum(basis[:, None]*residual, axis=0)/np.sum(basis**2)
            fitted = (1-t[:, None])**2*begin + basis[:, None]*control + t[:, None]**2*end
            if np.mean(np.sum((fitted-q)**2, axis=1)) < 5:
                emit(1, [*begin, *control, *end], name + " quadratic fit", filled=False)
            basis = np.column_stack([3*(1-t)**2*t, 3*(1-t)*t**2])
            residual = q - (1-t[:, None])**3*begin - t[:, None]**3*end
            controls = np.linalg.lstsq(basis, residual, rcond=None)[0]
            fit = (1-t[:, None])**3*begin + basis@controls + t[:, None]**3*end
            if np.mean(np.sum((fit-q)**2, axis=1)) < 3:
                emit(2, [*begin, *controls[0], *controls[1], *end], name + " cubic fit", filled=False)
            # Least-squares circle, converted from screen angles to Arc2D angles.
            matrix = np.column_stack([2*q[:, 0], 2*q[:, 1], np.ones(len(q))])
            cx, cy, c = np.linalg.lstsq(matrix, np.sum(q*q, axis=1), rcond=None)[0]
            radius = np.sqrt(max(0, c+cx*cx+cy*cy))
            if 3 < radius < 160:
                radial = np.linalg.norm(q-[cx, cy], axis=1)
                if np.mean((radial-radius)**2) < 1.2:
                    angles = np.unwrap(np.arctan2(-(q[:, 1]-cy), q[:, 0]-cx))
                    extent = np.degrees(angles[-1]-angles[0])
                    if 12 < abs(extent) < 330:
                        emit(3, [cx-radius, cy-radius, 2*radius, 2*radius,
                             np.degrees(angles[0]), extent], name + " circular arc fit", filled=False)
            segments += 1
            if segments >= (1500, 3000, 5000)[quality]:
                break
        if segments >= (1500, 3000, 5000)[quality]:
            break
    cascade = cv2.CascadeClassifier(str(Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"))
    faces = [] if cascade.empty() else cascade.detectMultiScale(gray, 1.12, 4, minSize=(40, 40))
    face_list = [[int(v) for v in box] for box in faces]
    with (directory / "proposals.tsv").open("w", encoding="utf-8") as stream:
        stream.write("\n".join(proposals))
    metadata = {"method": "OpenCV color regions, contour topology, least-squares curves and arcs",
                "style": style, "recolor": recolor, "strength": strength, "style_method": "deterministic non-neural image processing",
                "width": width, "height": height, "palette_colors": colors,
                "region_count": len(regions), "edge_segments": segments,
                "proposal_counts": counts, "face_boxes": face_list,
                "face_boxes_are_optional": True, "opencv_version": cv2.__version__}
    (directory / "analysis.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    (directory / "faces.tsv").write_text("\n".join(",".join(map(str, box)) for box in face_list), encoding="ascii")
    print(f"Extracted {len(regions)} color regions and {segments} edge segments; {len(proposals)} shape proposals.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("directory")
    parser.add_argument("--quality", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--style", choices=STYLES, default="original")
    parser.add_argument("--recolor", choices=FILTERS, default="none")
    parser.add_argument("--strength", type=int, choices=range(101), default=60)
    args = parser.parse_args()
    analyze(args.source, args.directory, args.quality, args.style, args.recolor, args.strength)
