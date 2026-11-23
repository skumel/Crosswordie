# Crosswordie 🟩🟦🟨

**Clueless Crossword × Wordle Deduction** — An original, offline-first Android puzzle game built with **Kotlin** and **Jetpack Compose (Material 3)**.

## 📥 Download APK
- **[Download `Crosswordie-v1.0.apk` from Releases](https://github.com/skumel/Crosswordie/releases/latest)**
- Or download [`Crosswordie-v1.0.apk`](./Crosswordie-v1.0.apk) directly from the repository root.

---

## 🎮 Gameplay Features
- **Clueless Crossword + Wordle Deduction:** Tap any Across or Down word slot in the 5×5 or 6×6 crossword grid to load it into the active 5-guess Wordle panel below.
- **The Cascade Hook 🔒:** When you solve a word, it locks into the crossword grid. Every intersecting letter shared with unsolved perpendicular words automatically pre-fills as a locked **Green Hint**!
- **Zero Soft-Locks:** If you miss all 5 guesses on a word, it reveals with 0 points (`+1 Strike`) and still unlocks its intersecting letters so you can always finish the board.
- **3 Difficulty Levels:**
  - **Easy:** `5×5 Grid • 6 Words (3–5 letters) • 1 Free Starter Letter`
  - **Medium:** `6×6 Grid • 6–7 Words (3–6 letters) • Classic Deduction`
  - **Hard:** `6×6 Dense Grid • 7–8 Words (3–6 letters) • Maximum Challenge`
- **Daily Seeded Puzzle & Endless Mode:** Deterministic daily puzzles with shareable spoiler-free emoji grids, plus unlimited procedural Endless levels.
- **100% Offline-First:** Bundled 12,000+ English word dictionary (including singular & plural forms for 3–6 letter words) and local state persistence.
