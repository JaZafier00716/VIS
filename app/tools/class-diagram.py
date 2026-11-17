#!/usr/bin/env python3
# Usage: python3 tools/class-diagram.py   (run from app/; writes docs/diagrams/class-diagram*.puml and renders PNG+SVG for them and for docs/diagrams/patterns/*.puml, needs java + plantuml.jar)
import re, subprocess, sys
from pathlib import Path

APP = Path(__file__).resolve().parent.parent
SRC = APP / "src/main/java"
OUT = APP / "docs/diagrams"
PLANTUML = "/usr/share/plantuml/plantuml.jar"
BASE = "cz.vsb.catering"
MODS = r"(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\s+)*"
DECL = re.compile(r"(?m)^([ \t]*)(" + MODS + r")(class|interface|enum|record)\s+(\w+)")
STRIP = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])+\'|//[^\n]*|/\*(?:.|\n)*?\*/')
LAYERS = {"presentation": "presentation", "business": "business", "data": "data"}


def strip_generics(s):
    while True:
        t = re.sub(r"<[^<>]*>", "", s)
        if t == s:
            return s
        s = t


def match_brace(t, i):
    d = 0
    for k in range(i, len(t)):
        if t[k] == "{":
            d += 1
        elif t[k] == "}":
            d -= 1
            if d == 0:
                return k
    raise ValueError("unbalanced braces")


def flatten(t):  # keep depth-0 text, collapse every {...} to {}
    out, d = [], 0
    for c in t:
        if c == "{":
            if d == 0:
                out.append("{}")
            d += 1
        elif c == "}":
            d -= 1
        elif d == 0:
            out.append(c)
    return "".join(out)


def split_top(s, sep=","):
    parts, d, cur = [], 0, ""
    for c in s:
        d += c in "<(" ; d -= c in ">)"
        if c == sep and d == 0:
            parts.append(cur); cur = ""
        else:
            cur += c
    if cur.strip():
        parts.append(cur)
    return [p.strip() for p in parts]


def norm(s):
    return re.sub(r"\s+", " ", s).strip()


def parse_file(path):
    text = STRIP.sub(lambda m: " " * len(m.group()) if m.group().startswith("/") else '""', path.read_text(encoding="utf-8"))
    pkg = re.search(r"(?m)^\s*package\s+([\w.]+);", text).group(1)
    decls = []
    for m in DECL.finditer(text):
        mods, kind, name = m.group(2), m.group(3), m.group(4)
        b = text.index("{", m.end())
        e = match_brace(text, b)
        header = text[m.end():b]
        decls.append(dict(pkg=pkg, name=name, kind=kind, mods=mods.split(), start=m.start(), b=b, e=e, header=header, text=text))
    res = []
    for d in decls:
        nested = [n for n in decls if n is not d and d["b"] < n["start"] and n["e"] < d["e"]]
        body = d["text"][d["b"] + 1:d["e"]]
        # remove nested type declarations (offsets relative to body)
        for n in sorted(nested, key=lambda n: -n["start"]):
            if any(o is not n and o["b"] < n["start"] and n["e"] < o["e"] and o in nested for o in decls):
                continue  # grand-children vanish with their parent
            body = body[:n["start"] - d["b"] - 1] + body[n["e"] - d["b"]:]
        d["flat"] = flatten(body)
        outers = [o for o in decls if o is not d and o["b"] < d["start"] and d["e"] < o["e"]]
        d["outer"] = min(outers, key=lambda o: o["e"] - o["b"])["name"] if outers else None
        d["private"] = "private" in d["mods"]
        res.append(d)
    return res


def analyse(d):
    h = strip_generics(d["header"])
    d["comps"] = []
    if d["kind"] == "record":
        i = h.index("(")
        depth, k = 0, i
        while True:
            depth += h[k] == "("; depth -= h[k] == ")"
            if depth == 0:
                break
            k += 1
        d["comps"] = split_top(h[i + 1:k])
        h = h[k + 1:]
    ext = re.search(r"\bextends\s+([\w\s.,]+?)(?:\bimplements\b|$)", h)
    imp = re.search(r"\bimplements\s+([\w\s.,]+)", h)
    names = lambda m: [x.strip().split(".")[-1] for x in m.group(1).split(",") if x.strip()] if m else []
    d["extends"], d["implements"] = names(ext), names(imp)
    d["fields"], d["methods"] = [], []
    if d["kind"] == "enum":
        return
    for stmt in re.split(r";|\{\}", d["flat"]):
        s = norm(re.sub(r"@\w+(\([^)]*\))?", " ", stmt))
        if not s:
            continue
        head = s.split("=")[0]
        if "(" in head:
            before, rest = head.split("(", 1)
            before = norm(strip_generics_lead(before))
            toks = before.split(" ")
            mname = toks[-1]
            if mname == d["name"] or len(toks) < 1:
                continue  # constructor
            mods = [t for t in toks[:-1] if t in ("public", "protected", "private", "static", "abstract", "default", "final", "synchronized")]
            ret = " ".join(t for t in toks[:-1] if t not in mods) or "void"
            params = head[head.index("(") + 1: head.rindex(")")] if ")" in head else rest
            ptypes = [norm(re.sub(r"\bfinal\s+", "", p)).rsplit(" ", 1)[0] for p in split_top(params)]
            public = "public" in mods or (d["kind"] == "interface" and "private" not in mods)
            d["methods"].append(dict(name=mname, ret=ret, params=ptypes, static="static" in mods, abstract="abstract" in mods, public=public))
        else:
            toks = [t for t in head.split(" ") if t not in ("private", "public", "protected", "static", "final", "volatile", "transient")]
            if len(toks) >= 2:
                d["fields"].append(" ".join(toks[:-1]))
    for c in d["comps"]:
        d["fields"].append(c.rsplit(" ", 1)[0])


def strip_generics_lead(s):
    return re.sub(r"^\s*((?:\w+\s+)*)<[^>]*>\s*", r"\1", s)


def is_accessor(m):
    if m["static"]:
        return False
    return (re.match(r"(get|is)[A-Z]", m["name"]) and not m["params"]) or (re.match(r"set[A-Z]", m["name"]) and len(m["params"]) == 1)


def layer_of(pkg):
    parts = pkg[len(BASE):].strip(".").split(".")
    return LAYERS.get(parts[0])


def short_pkg(pkg):
    return pkg[len(BASE):].strip(".") or "root"


def load():
    classes = []
    for p in sorted(SRC.rglob("*.java")):
        for d in parse_file(p):
            if not d["private"]:
                classes.append(d)
    for d in classes:
        analyse(d)
    for c in classes:  # nested types get a unique id Outer_Inner and the label Outer.Inner
        c["id"] = c["name"] if not c["outer"] else c["outer"] + "_" + c["name"]
        c["label"] = c["name"] if not c["outer"] else c["outer"] + "." + c["name"]
    ids = [c["id"] for c in classes]
    assert len(ids) == len(set(ids)), "duplicate class ids"
    return classes


def relations(classes):
    cnt = {}
    for c in classes:
        cnt[c["name"]] = cnt.get(c["name"], 0) + 1
    known = {c["name"]: c["id"] for c in classes if cnt[c["name"]] == 1}  # ambiguous nested names are skipped
    inh, assoc = [], set()
    for c in classes:
        for p in c["extends"]:
            if p in known:
                inh.append((c["id"], known[p], "--|>"))
        for p in c["implements"]:
            if p in known:
                inh.append((c["id"], known[p], "..|>"))
        for f in c["fields"]:
            for ident in set(re.findall(r"[A-Za-z_]\w*", f)):
                if ident in known and known[ident] != c["id"]:
                    assoc.add((c["id"], known[ident]))
    # collapse: drop an association if an inheritance link already joins the two classes
    linked = {(a, b) for a, b, _ in inh} | {(b, a) for a, b, _ in inh}
    assoc = sorted(a for a in assoc if a not in linked)
    return inh, assoc


HEAD = """@startuml
skinparam backgroundColor white
skinparam shadowing false
skinparam classAttributeIconSize 0
skinparam packageStyle rectangle
skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #444444
  BackgroundColor<<external>> #EEEEEE
  BorderColor<<external>> #999999
}
"""


def decl(c, members):
    kw = {"interface": "interface", "enum": "enum", "record": "class"}.get(c["kind"], "abstract class" if "abstract" in c["mods"] else "class")
    st = " <<record>>" if c["kind"] == "record" else ""
    nm = c["id"] if c["id"] == c["label"] else f'"{c["label"]}" as {c["id"]}'
    if not members:
        return f"{kw} {nm}{st}"
    lines = []
    for m in c["methods"]:
        if m["public"] and not is_accessor(m):
            lines.append("  " + ("{static} " if m["static"] else "") + f"{m['name']}({', '.join(m['params'])}) : {m['ret']}")
    return f"{kw} {nm}{st} {{\n" + "\n".join(lines) + "\n}" if lines else f"{kw} {nm}{st}"


def render_all(classes, inh, assoc):
    # readability: associations into business.domain from other packages and all AppContext edges are left out
    pk = {c["id"]: c["pkg"] for c in classes}
    keep = [(a, b) for a, b in assoc if "AppContext" not in (a, b) and (pk[a] == pk[b] or not pk[b].endswith(".domain"))]
    o = [HEAD, "left to right direction", "hide members", "hide empty members", "skinparam ArrowColor #666666", ""]
    for layer in ("presentation", "business", "data", None):
        pk_l = sorted({c["pkg"] for c in classes if layer_of(c["pkg"]) == layer})
        if layer:
            o.append(f'package "{layer} layer" <<Rectangle>> {{')
        for pkg in pk_l:
            o.append(f'package "{short_pkg(pkg)}" {{')
            o += ["  " + decl(c, False) for c in classes if c["pkg"] == pkg]
            o.append("}")
        if layer:
            o.append("}")
    o.append('note right of AppContext : composition root, creates and wires\\nall services, mappers and the storage')
    o += [f"{a} {r} {b}" for a, b, r in inh]
    o += [f"{a} --> {b}" for a, b in keep]
    o.append("@enduml")
    return "\n".join(o) + "\n"


def render_layer(classes, inh, assoc, layer):
    mine = {c["id"]: c for c in classes if layer_of(c["pkg"]) == layer}
    byname = {c["id"]: c for c in classes}
    inh_l = [x for x in inh if x[0] in mine]
    assoc_l = [x for x in assoc if x[0] in mine]
    ext = sorted({n for a, b, _ in inh_l for n in (a, b)} | {n for a, b in assoc_l for n in (a, b)} - set(mine))
    ext = [n for n in ext if n not in mine]
    o = [HEAD, "left to right direction", "hide empty members", ""]
    for pkg in sorted({c["pkg"] for c in mine.values()}):
        o.append(f'package "{short_pkg(pkg)}" {{')
        o += ["  " + decl(c, True).replace("\n", "\n  ") for c in classes if c["pkg"] == pkg and c["id"] in mine]
        o.append("}")
    for n in ext:
        c = byname[n]
        kw = "interface" if c["kind"] == "interface" else "class"
        o.append(f"{kw} {n} <<external>>")
    o += [f"{a} {r} {b}" for a, b, r in inh_l]
    o += [f"{a} --> {b}" for a, b in assoc_l]
    o.append("@enduml")
    return "\n".join(o) + "\n"


def render(puml, fmt):
    subprocess.run(["java", "-DPLANTUML_LIMIT_SIZE=8192", "-jar", PLANTUML, "-charset", "UTF-8", fmt, str(puml)], check=True)


def main():
    classes = load()
    inh, assoc = relations(classes)
    OUT.mkdir(parents=True, exist_ok=True)
    files = {OUT / "class-diagram.puml": render_all(classes, inh, assoc)}
    for layer in ("presentation", "business", "data"):
        files[OUT / f"class-diagram-{layer}.puml"] = render_layer(classes, inh, assoc, layer)
    for f, txt in files.items():
        f.write_text(txt, encoding="utf-8")
    allp = files[OUT / "class-diagram.puml"]
    for known in ("MenuService", "Folder", "OracleMenuMapper"):  # self-check
        assert known in allp, known
    assert "Folder --|> ProjectNode" in allp and "OracleMenuMapper ..|> MenuMapper" in allp
    assert "price(" in files[OUT / "class-diagram-business.puml"] or "PriceStrategy" in files[OUT / "class-diagram-business.puml"]
    print(len(classes), "classes,", len(inh), "inheritance,", len(assoc), "associations")
    if "--no-render" not in sys.argv:
        for f in list(files) + sorted((OUT / "patterns").glob("*.puml")):
            for fmt in ("-tpng", "-tsvg"):
                render(f, fmt)


if __name__ == "__main__":
    main()
