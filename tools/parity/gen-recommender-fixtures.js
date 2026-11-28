#!/usr/bin/env node
/**
 * Generates parity fixtures for the Kotlin port of the "For you" recommender.
 *
 * The JS below (TOPICS, STOP, domainOf, topicOf, featuresOf, featureLabel,
 * SIGNALS and the Learner object) is copied VERBATIM from the web app
 * (https://github.com/agent7jacob-7177/code7 gh-pages index.html) and is the
 * specification. The only adaptation is a `store` stub so the Learner object
 * can be evaluated outside a browser; scenario events/impressions are
 * assigned onto the Learner after construction.
 *
 * Run: node tools/parity/gen-recommender-fixtures.js
 * Output: app/src/test/resources/recommender/scenario_<A|B|C>.json
 */
"use strict";

const fs = require("fs");
const path = require("path");

// --- verbatim: localStorage stub (web app has the real one) -----------------
const store = {
  get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};
global.localStorage = { _m: new Map(), getItem(k) { return this._m.has(k) ? this._m.get(k) : null; }, setItem(k, v) { this._m.set(k, String(v)); } };

// --- verbatim: TOPICS (web app) ----------------------------------------------
const TOPICS = [
  { id: "", label: "All" },
  { id: "AI", label: "AI", q: "AI", match: /\b(ai|llms?|gpt|chatgpt|openai|anthropic|claude|gemini|agi|chatbot|copilot)\b/i },
  { id: "ML", label: "Machine Learning", tag: "ML", q: "machine learning",
    match: /\b(machine learning|ml|deep learning|neural (net|network)s?|pytorch|tensorflow|jax|transformers?|diffusion|reinforcement learning|rlhf|fine-?tun\w*|embeddings?|inference|training run|datasets?|kaggle|scikit-learn|xgboost|open weights|model weights)\b/i },
  { id: "Security", label: "Security", q: "security", match: /\b(security|vulnerab|breach|hack|exploit|cve|malware|ransomware|privacy)\b/i },
  { id: "Programming", label: "Programming", q: "programming", match: /\b(rust|python|javascript|typescript|golang|go |compiler|programming|code|developer|api|framework)\b/i },
  { id: "Hardware", label: "Hardware", q: "chip", match: /\b(chip|cpu|gpu|nvidia|amd|intel|arm|apple silicon|semiconductor|hardware)\b/i },
  { id: "Space", label: "Space", q: "space", match: /\b(nasa|spacex|rocket|satellite|orbit|space|mars|moon)\b/i },
  { id: "Startups", label: "Startups", q: "startup", match: /\b(startup|funding|raises|vc|acquire|acquisition|ipo|series [a-d])\b/i },
];

// --- verbatim: STOP (web app) -------------------------------------------------
const STOP = new Set(("a an the and or of to in on for with by from at as is are was were be been being it its this that these those " +
  "how why what when where who which your you we our i my me vs via into about over after before new show ask hn not no can could will " +
  "would should just using use used make made get got than then there their they them his her do does did done one two first more most " +
  "all any some out up down off only also like way ways years year day days").split(" "));

// --- verbatim: domainOf / topicOf (web app) -----------------------------------
function domainOf(url) { try { return new URL(url).hostname.replace(/^www\./, ""); } catch { return ""; } }
function topicOf(title) {
  const t = TOPICS.find(t => t.match && t.match.test(title));
  return t ? (t.tag || t.label) : "";
}

// --- verbatim: featuresOf / featureLabel (web app) ----------------------------
function featuresOf(h) {
  const title = h.title || "";
  const words = title.toLowerCase().replace(/[^a-z0-9+#.\-\s]/g, " ").split(/\s+/)
    .map(w => w.replace(/^[.\-]+|[.\-]+$/g, ""))
    .filter(w => w.length > 1 && !STOP.has(w) && !/^\d+$/.test(w));
  const f = new Set(words.map(w => "w:" + w));
  for (let i = 0; i + 1 < words.length; i++) f.add(`b:${words[i]} ${words[i + 1]}`);
  const d = domainOf(h.url); if (d) f.add("d:" + d);
  const t = topicOf(title); if (t) f.add("t:" + t);
  if (/^show hn/i.test(title)) f.add("k:Show HN");
  if (/^ask hn/i.test(title)) f.add("k:Ask HN");
  return [...f];
}
const featureLabel = (f) => {
  const [kind, v] = [f.slice(0, 1), f.slice(2)];
  return kind === "t" ? (TOPICS.find(t => (t.tag || t.label) === v) || { label: v }).label : v;
};

// --- verbatim: SIGNALS (web app) ----------------------------------------------
const SIGNALS = { open: 0.4, save: 0.8, read: 1 };

// --- verbatim: Learner (web app; store stubbed above) --------------------------
const Learner = {
  events: store.get("tu.events") || [],   // { k: open|save|read, h, at }
  imp: store.get("tu.imp") || {},         // id -> { n, h, at }  (times seen without opening)
  model: null,
  seen: new Set(),

  record(kind, h) {
    const id = String(h.objectID);
    this.events = this.events.filter(e => !(e.k === kind && e.h.objectID === id));
    this.events.push({ k: kind, h: slim(h), at: Date.now() });
    if (this.events.length > 600) this.events = this.events.slice(-600);
    this.model = null;
    store.set("tu.events", this.events);
  },
  forget(kind, id) {
    this.events = this.events.filter(e => !(e.k === kind && e.h.objectID === String(id)));
    this.model = null;
    store.set("tu.events", this.events);
  },
  impression(h) {
    const id = String(h.objectID);
    if (this.seen.has(id)) return;          // once per page load
    this.seen.add(id);
    const cur = this.imp[id] || { n: 0 };
    this.imp[id] = { n: cur.n + 1, h: slim(h), at: Date.now() };
    const keys = Object.keys(this.imp);
    if (keys.length > 400) keys.sort((a, b) => this.imp[a].at - this.imp[b].at).slice(0, keys.length - 400).forEach(k => delete this.imp[k]);
    this.model = null;
    clearTimeout(this._t);
    this._t = setTimeout(() => store.set("tu.imp", this.imp), 500);
  },
  positiveIds() { return new Set(this.events.map(e => e.h.objectID)); },
  positives() { return this.positiveIds().size; },
  reset() { this.events = []; this.imp = {}; this.model = null; store.set("tu.events", []); store.set("tu.imp", {}); },

  examples() {
    const pos = new Map();   // one example per story, strongest signal wins
    for (const e of this.events) {
      const w = SIGNALS[e.k] || 0.4, cur = pos.get(e.h.objectID);
      if (!cur || cur.s < w) pos.set(e.h.objectID, { h: e.h, y: 1, s: w });
    }
    const data = [...pos.values()];
    for (const [id, v] of Object.entries(this.imp)) {
      if (!pos.has(id) && v.n >= 1) data.push({ h: v.h, y: 0, s: Math.min(0.45, 0.15 * v.n) });
    }
    return data.map(d => ({ x: featuresOf(d.h), y: d.y, s: d.s }));
  },

  train() {
    const data = this.examples();
    const w = new Map(); let b = 0;
    const lr = 0.5, l2 = 0.002, epochs = 14;
    let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    for (let ep = 0; ep < epochs; ep++) {
      for (let i = data.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [data[i], data[j]] = [data[j], data[i]]; }
      for (const d of data) {
        if (!d.x.length) continue;
        const xv = 1 / Math.sqrt(d.x.length);
        let z = b; for (const f of d.x) z += (w.get(f) || 0) * xv;
        const g = (d.y - 1 / (1 + Math.exp(-z))) * d.s;
        b += lr * 0.1 * g;
        for (const f of d.x) { const wf = w.get(f) || 0; w.set(f, wf + lr * (g * xv - l2 * wf)); }
      }
    }
    // How many stories you engaged with share each feature: only recurring ones are shown as interests/reasons
    const support = new Map();
    data.filter(d => d.y === 1).forEach(d => d.x.forEach(f => support.set(f, (support.get(f) || 0) + 1)));
    this.model = { w, b, n: data.length, support };
    return this.model;
  },

  // Returns the learned affinity for a story (independent of the bias) and the top reasons
  score(h) {
    const m = this.model || this.train();
    const x = featuresOf(h);
    if (!x.length) return { affinity: 0, why: [] };
    const xv = 1 / Math.sqrt(x.length);
    const parts = x.map(f => [f, (m.w.get(f) || 0) * xv]);
    const affinity = parts.reduce((s, [, c]) => s + c, 0);
    const why = parts.filter(([f, c]) => c > 0.04 && (m.support.get(f) || 0) >= 2).sort((a, b) => b[1] - a[1])
      .map(([f]) => featureLabel(f)).filter((v, i, a) => a.indexOf(v) === i).slice(0, 2);
    return { affinity, why };
  },

  interests(n = 10) {
    const m = this.model || this.train();
    const out = [];
    for (const [f, wt] of [...m.w.entries()].sort((a, b) => b[1] - a[1])) {
      if (wt < 0.05 || out.length >= n) break;
      if ((m.support.get(f) || 0) < 2) continue;
      const label = featureLabel(f);
      if (!out.some(o => o === label || o.includes(label))) out.push(label);
    }
    return out;
  },
};

// The verbatim Learner methods above only use slim() via record/impression,
// which this generator never calls; keep a reference so the verbatim block is
// self-contained.
function slim(h) {
  return {
    objectID: String(h.objectID), title: h.title, url: h.url || null, author: h.author,
    points: h.points || 0, num_comments: h.num_comments || 0, created_at_i: h.created_at_i,
    story_text: h.story_text ? String(h.story_text).slice(0, 4000) : null,
  };
}

// =============================================================================
// Deterministic dataset: 45 realistic HN stories covering every topic,
// Show HN / Ask HN, URLs with and without www, and titles with no features.
// =============================================================================
const STORIES = [
  // AI (6)
  { id: "s01", title: "OpenAI releases GPT-5 with improved reasoning", url: "https://www.openai.com/blog/gpt-5" },
  { id: "s02", title: "Claude 4 benchmarks and what they mean", url: "https://anthropic.com/claude-4" },
  { id: "s03", title: "Anthropic publishes its AI safety report", url: "https://www.anthropic.com/safety" },
  { id: "s04", title: "Gemini 2.5 review: a year of chatbot progress", url: "https://blog.google/gemini-2-5" },
  { id: "s05", title: "ChatGPT memory feature explained", url: "https://openai.com/chatgpt-memory" },
  { id: "s06", title: "AGI timelines: a sober look", url: "https://example-ai.org/agi-timelines" },
  // ML (7)
  { id: "s07", title: "PyTorch 3.0 released", url: "https://pytorch.org/blog/pytorch-3-0" },
  { id: "s08", title: "TensorFlow vs JAX in 2026", url: "https://www.tensorflow.org/blog/tf-vs-jax" },
  { id: "s09", title: "Fine-tuning LLMs on edge devices", url: "https://huggingface.co/blog/edge-finetuning" },
  { id: "s10", title: "A survey of diffusion models", url: "https://arxiv.org/abs/2601.01234" },
  { id: "s11", title: "Kaggle tabular competition results", url: "https://www.kaggle.com/competitions/tabular-2026" },
  { id: "s12", title: "XGBoost still wins tabular data", url: "https://dmlc.ai/blog/xgboost-2026" },
  { id: "s13", title: "scikit-learn 1.7 and open weights models", url: "https://scikit-learn.org/stable/whats_new" },
  // Security (6)
  { id: "s14", title: "Major breach at a cloud provider", url: "https://www.securityweek.com/cloud-breach" },
  { id: "s15", title: "New CVE in the Linux kernel", url: "https://kernel.org/security/cve-2026-1234" },
  { id: "s16", title: "Ransomware targets hospitals again", url: "https://www.bleepingcomputer.com/ransomware" },
  { id: "s17", title: "Privacy regulations in 2026", url: "https://iapp.org/privacy-regulations" },
  { id: "s18", title: "An exploit chain, dissected", url: "https://thehackernews.com/exploit-chain" },
  { id: "s19", title: "Malware found in npm packages", url: "https://www.snyk.com/blog/npm-malware" },
  // Programming (8)
  { id: "s20", title: "Rust 2.0 announced", url: "https://www.rust-lang.org/blog/rust-2-0" },
  { id: "s21", title: "Python 3.14 release notes", url: "https://docs.python.org/3/whatsnew/3.14.html" },
  { id: "s22", title: "TypeScript 6.0: what changed", url: "https://www.typescriptlang.org/blog/ts-6" },
  { id: "s23", title: "Go 1.25 performance improvements", url: "https://go.dev/blog/go1.25" },
  { id: "s24", title: "Golang vs Rust for backend services", url: "https://example.dev/golang-vs-rust" },
  { id: "s25", title: "Notes on compiler design", url: "https://www.cs.cmu.edu/compilers/notes" },
  { id: "s26", title: "API design patterns that scale", url: "https://www.iamnotapi.com/api-design" },
  { id: "s27", title: "Developer productivity survey 2026", url: "https://survey.dev/productivity" },
  // Hardware (6)
  { id: "s28", title: "Nvidia unveils the B400 GPU", url: "https://www.nvidia.com/en-us/data-center/b400" },
  { id: "s29", title: "AMD Ryzen 10000 series review", url: "https://www.amd.com/en/ryzen-10000" },
  { id: "s30", title: "Intel 18A process in production", url: "https://www.intel.com/content/www/us/en/newsroom/18a.html" },
  { id: "s31", title: "Apple Silicon M6 inside the new Mac", url: "https://www.apple.com/mac/m6" },
  { id: "s32", title: "ARM servers keep rising", url: "https://www.arm.com/servers" },
  { id: "s33", title: "Chip fabrication: the next decade", url: "https://www.semiconductors.org/fab-2036" },
  // Space (6)
  { id: "s34", title: "NASA Artemis launch goes ahead", url: "https://www.nasa.gov/artemis-launch" },
  { id: "s35", title: "SpaceX Starship completes orbital test", url: "https://www.spacex.com/starship" },
  { id: "s36", title: "Rocket recovery test succeeds", url: "https://arstechnica.com/rocket-recovery" },
  { id: "s37", title: "Satellite internet expansion continues", url: "https://www.satellitedaily.com/expansion" },
  { id: "s38", title: "Mars rover finds new mineral", url: "https://mars.nasa.gov/rover-mineral" },
  { id: "s39", title: "Moon base plans take shape", url: "https://moonbase.example.org/plans" },
  // Startups (5)
  { id: "s40", title: "Startup raises $100M Series B", url: "https://techcrunch.com/series-b" },
  { id: "s41", title: "Acquisition of a dev tools company", url: "https://www.theverge.com/acquisition" },
  { id: "s42", title: "IPO of a cloud firm priced", url: "https://www.reuters.com/cloud-ipo" },
  { id: "s43", title: "VC winter may finally end", url: "https://www.pwc.com/vc-winter" },
  { id: "s44", title: "Funding rounds hit a record in Q3", url: "https://crunchbase.com/funding-q3" },
  // Show HN / Ask HN (4)
  { id: "s45", title: "Show HN: A terminal-based git client", url: "https://news.ycombinator.com/item?id=45000001" },
  { id: "s46", title: "Show HN: Build a website in 60 seconds", url: "https://www.showhn.dev/website-60s" },
  { id: "s47", title: "Ask HN: How do you manage context switching?", url: null },
  { id: "s48", title: "Ask HN: What is your dev setup in 2026?", url: null },
  // No-feature titles (2)
  { id: "s49", title: "The new", url: null },
  { id: "s50", title: "!!!", url: "https://example.com/no-words" },
  // Extra AI story sharing the "openai" word feature with s01 (2)
  { id: "s51", title: "OpenAI developer platform update", url: "https://openai.com/platform" },
  { id: "s52", title: "Copilot and chatbot usage at work", url: "https://www.example-copilot.org/usage" },
];

const byId = new Map(STORIES.map(s => [s.id, { objectID: s.id, title: s.title, url: s.url || null }]));

// =============================================================================
// Scenarios
// =============================================================================
const T0 = 1764000000000; // fixed base timestamp (ms)
const MIN = 60000;

const SCENARIOS = {
  A: {
    // Few open events only, no impressions.
    events: [
      { kind: "open", id: "s01", at: T0 + 1 * MIN },
      { kind: "open", id: "s02", at: T0 + 2 * MIN },
      { kind: "open", id: "s07", at: T0 + 3 * MIN },
      { kind: "open", id: "s20", at: T0 + 4 * MIN },
      { kind: "open", id: "s45", at: T0 + 5 * MIN },
    ],
    impressions: [],
  },
  B: {
    // Mixed open/save/read + impressions with n = 1..4.
    // s01 gets open then read (strongest signal wins); s09 gets open+save
    // (save wins); s14 is only an impression (negative).
    events: [
      { kind: "open", id: "s01", at: T0 + 1 * MIN },
      { kind: "open", id: "s07", at: T0 + 2 * MIN },
      { kind: "save", id: "s09", at: T0 + 3 * MIN },
      { kind: "open", id: "s09", at: T0 + 4 * MIN },
      { kind: "read", id: "s01", at: T0 + 5 * MIN },
      { kind: "open", id: "s14", at: T0 + 6 * MIN },
      { kind: "save", id: "s28", at: T0 + 7 * MIN },
      { kind: "read", id: "s34", at: T0 + 8 * MIN },
      { kind: "open", id: "s40", at: T0 + 9 * MIN },
      { kind: "open", id: "s47", at: T0 + 10 * MIN },
      { kind: "open", id: "s03", at: T0 + 11 * MIN },
      { kind: "open", id: "s05", at: T0 + 12 * MIN },
      { kind: "open", id: "s08", at: T0 + 13 * MIN },
      { kind: "open", id: "s21", at: T0 + 14 * MIN },
      { kind: "open", id: "s29", at: T0 + 15 * MIN },
      { kind: "open", id: "s35", at: T0 + 16 * MIN },
      { kind: "open", id: "s41", at: T0 + 17 * MIN },
      { kind: "open", id: "s51", at: T0 + 18 * MIN },
      { kind: "open", id: "s52", at: T0 + 19 * MIN },
    ],
    impressions: [
      { id: "s14", n: 4, at: T0 + 20 * MIN },
      { id: "s22", n: 3, at: T0 + 21 * MIN },
      { id: "s30", n: 2, at: T0 + 22 * MIN },
      { id: "s43", n: 1, at: T0 + 23 * MIN },
      { id: "s49", n: 2, at: T0 + 24 * MIN },
    ],
  },
  C: {
    // Empty: no events, no impressions.
    events: [],
    impressions: [],
  },
};

// =============================================================================
// Fixture generation
// =============================================================================
function buildScenario(name, spec) {
  Learner.model = null;
  Learner.events = spec.events.map(e => ({ k: e.kind, h: byId.get(e.id), at: e.at }));
  Learner.imp = {};
  for (const imp of spec.impressions) Learner.imp[imp.id] = { n: imp.n, h: byId.get(imp.id), at: imp.at };

  const features = {};
  for (const s of STORIES) features[s.id] = featuresOf(byId.get(s.id));

  const model = Learner.train();
  const w = [...model.w.entries()];
  const support = [...model.support.entries()];

  const scores = {};
  for (const s of STORIES) {
    const r = Learner.score(byId.get(s.id));
    scores[s.id] = { affinity: r.affinity, why: r.why };
  }

  return {
    scenario: name,
    stories: STORIES.map(s => ({ id: s.id, title: s.title, url: s.url || null })),
    events: spec.events,
    impressions: spec.impressions,
    features,
    model: { w, b: model.b, n: model.n, support },
    scores,
    interests: Learner.interests(10),
  };
}

const outDir = path.join(__dirname, "..", "..", "app", "src", "test", "resources", "recommender");
fs.mkdirSync(outDir, { recursive: true });
for (const name of ["A", "B", "C"]) {
  const fixture = buildScenario(name, SCENARIOS[name]);
  const file = path.join(outDir, `scenario_${name}.json`);
  fs.writeFileSync(file, JSON.stringify(fixture, null, 2) + "\n");
  console.log(`wrote ${file} (stories=${fixture.stories.length}, events=${fixture.events.length}, impressions=${fixture.impressions.length}, n=${fixture.model.n}, w=${fixture.model.w.length}, support=${fixture.model.support.length}, interests=${JSON.stringify(fixture.interests)})`);
}
