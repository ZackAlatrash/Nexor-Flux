---
source: "USDA Agricultural Research Service. FoodData Central. United States Department of Agriculture. Available at: https://fdc.nal.usda.gov/. Data current through April 2026. License: CC0 1.0 Universal (public domain)."
tags: ["food-database", usda, food, foods, "high-protein", sources, macros, supplement]
---

## What is USDA FoodData Central and why does this app use it
tags: "food-database", usda, food, foods, macros, sources
USDA FoodData Central (FDC) is the official U.S. government nutrient composition database and the gold-standard source for food macronutrient and micronutrient data. It is published under CC0 1.0 Universal (public domain) — no licensing restrictions for use in apps or commercial products.

**Five data types available:**
- **Foundation Foods** — highest analytical quality; commodity and minimally processed foods; updated semi-annually. Best source for whole-food macros (chicken breast, oats, eggs, etc.).
- **Branded Foods** — commercial product nutrition label data from manufacturers; updated monthly. Enables branded product and barcode lookups. Latest update: April 2026.
- **SR Legacy (Standard Reference)** — historical dataset; last updated April 2018; no longer updated but widely referenced by third-party databases.
- **Survey Foods (FNDDS)** — used in NHANES national dietary surveys; updated every 2 years.
- **Experimental Foods** — peer-reviewed academic research data.

**Access options:**
- Web: fdc.nal.usda.gov (free, no login).
- RESTful JSON API (free; requires data.gov API key) — supports real-time food search and single-record retrieval.
- Bulk CSV/JSON download — full offline dataset for embedding in an app food library.

**Nutrient coverage per food record:** calories, protein, fat (total, saturated, mono, poly), carbohydrates, fiber, sugars, sodium, potassium, calcium, iron, zinc, vitamins A/C/D/E/K, B vitamins, and (for Foundation Foods) full amino acid and fatty acid profiles.

## How to look up food protein and calorie data accurately
tags: "food-database", usda, food, foods, macros, "high-protein", sources, protein
When looking up nutrient values for a specific food, prefer **Foundation Foods** data over SR Legacy for whole, minimally processed foods — Foundation Foods uses the most rigorous analytical methodology and is updated semi-annually.

**Practical reference values from USDA FDC (Foundation Foods):**
- Cooked skinless chicken breast: ~32 g protein, ~3 g fat, ~158 kcal per 100 g.
- Large egg (50 g): ~6 g protein, ~5 g fat, ~70 kcal.
- Oats (dry): ~17 g protein, ~7 g fat, ~389 kcal per 100 g.
- Greek yogurt (plain, non-fat): ~10 g protein, ~0 g fat, ~59 kcal per 100 g.
- Cottage cheese (2% fat): ~11 g protein, ~2 g fat, ~72 kcal per 100 g.

**For branded/packaged foods:** use Branded Foods data or scan the product barcode — values come directly from the manufacturer nutrition label and are updated monthly. Always check the serving size listed to avoid miscalculating.

The food library in this app draws on USDA FDC data, so nutrient values you see for common whole foods match the USDA government standard.
