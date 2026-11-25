package app.openhealth.core.citation

/**
 * Every formula returns a [CitationKey] so the "how this is calculated" sheet can name
 * its source (`pr.md` §6.4, `DESIGN.md` §7.5). A key with no entry here cannot be
 * referenced: [Citations] fails its own completeness test in the unit suite.
 */
enum class CitationKey {
    MIFFLIN_ST_JEOR_1990,
    US_NAVY_BODY_FAT_1984,
    WHO_BMI_2000,
    FFMI_SCHUTZ_2002,
    COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
    FAO_WHO_UNU_2004_PAL,
    EPLEY_LESUER_1997,
    BRZYCKI_1993,
    WISHNOFSKY_1958,
    CDC_WEIGHT_LOSS,
    SLEEP_CONSENSUS_2015,
    UCUM,
    FDA_DAILY_VALUES_2020,
    IOM_MACRONUTRIENTS_2005,
    USDA_FOODDATA_CENTRAL,
    OPEN_FOOD_FACTS,
    ESH_HYPERTENSION_2023,
    AHA_RESTING_HEART_RATE,
    WHO_PULSE_OXIMETRY_2011,
    GENEVA_BODY_TEMPERATURE_2019,
    ADA_GLYCEMIC_TARGETS_2026,
    APP_RULE,
}

/** One citable statement, with where it came from and what it is used for. */
data class Citation(
    val key: CitationKey,
    val title: String,
    val authors: String,
    val year: Int,
    val publication: String,
    val url: String,
    val supports: String,
    val licenceNote: String? = null,
)

/**
 * The citation table. It is a table of facts and identifiers, not copied text: each row
 * names a published source and the formula or range it backs. Entries that the app must
 * reproduce as attribution (the Compendium of Physical Activities and UCUM) also appear
 * in `NOTICE.md`.
 */
object Citations {
    private val entries: List<Citation> = listOf(
        Citation(
            key = CitationKey.MIFFLIN_ST_JEOR_1990,
            title = "A new predictive equation for resting energy expenditure in " +
                "healthy individuals",
            authors = "Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO",
            year = 1990,
            publication = "The American Journal of Clinical Nutrition 51(2):241-247",
            url = "https://doi.org/10.1093/ajcn/51.2.241",
            supports = "Basal metabolic rate (Mifflin-St Jeor equation)",
        ),
        Citation(
            key = CitationKey.US_NAVY_BODY_FAT_1984,
            title = "Prediction of percent body fat for U.S. Navy men and women " +
                "from body circumferences and height",
            authors = "Hodgdon JA, Beckett MB",
            year = 1984,
            publication = "Naval Health Research Center reports 84-11 and 84-29",
            url = "https://apps.dtic.mil/sti/citations/ADA143890",
            supports = "Body fat percentage from tape circumferences (US Navy method)",
        ),
        Citation(
            key = CitationKey.WHO_BMI_2000,
            title = "Obesity: preventing and managing the global epidemic",
            authors = "World Health Organization",
            year = 2000,
            publication = "WHO Technical Report Series 894",
            url = "https://www.who.int/publications/i/item/9241208945",
            supports = "Body mass index and its classification bands",
        ),
        Citation(
            key = CitationKey.FFMI_SCHUTZ_2002,
            title = "Fat-free mass index and fat mass index percentiles in Caucasians aged 18-98 y",
            authors = "Schutz Y, Kyle UU, Pichard C",
            year = 2002,
            publication = "International Journal of Obesity 26(7):953-960",
            url = "https://doi.org/10.1038/sj.ijo.0802037",
            supports = "Fat-free mass index (FFMI) and its height-normalised form",
        ),
        Citation(
            key = CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
            title = "2024 Adult Compendium of Physical Activities",
            authors = "Herrmann SD, Willis EA, Ainsworth BE, et al.",
            year = 2024,
            publication = "Journal of Sport and Health Science 13(1):6-12",
            url = "https://doi.org/10.1016/j.jshs.2023.10.005",
            supports = "MET values used for exercise energy expenditure",
            licenceNote =
            "Numeric MET values are cited facts; the Compendium text is not reproduced. " +
                "Attribution is recorded in NOTICE.md.",
        ),
        Citation(
            key = CitationKey.FAO_WHO_UNU_2004_PAL,
            title = "Human energy requirements: report of a joint FAO/WHO/UNU expert consultation",
            authors = "Food and Agriculture Organization, World Health Organization, " +
                "United Nations University",
            year = 2004,
            publication = "FAO Food and Nutrition Technical Report Series 1",
            url = "https://www.fao.org/4/y5686e/y5686e00.htm",
            supports = "Physical activity levels used to derive TDEE from BMR",
        ),
        Citation(
            key = CitationKey.EPLEY_LESUER_1997,
            title = "The accuracy of prediction equations for estimating 1-RM performance " +
                "in the bench press, squat, and deadlift",
            authors = "LeSuer DA, McCormick JH, Mayhew JL, Wasserstein RL, Arnold MD",
            year = 1997,
            publication = "The Journal of Strength and Conditioning Research 11(4):211-213",
            url = "https://doi.org/10.1519/1533-4287(1997)011%3C0211:TAOPEF%3E2.3.CO;2",
            supports = "The Epley equation used to estimate one-rep max, evaluated against " +
                "measured 1-RM",
        ),
        Citation(
            key = CitationKey.BRZYCKI_1993,
            title = "Strength testing: predicting a one-rep max from reps-to-fatigue",
            authors = "Brzycki M",
            year = 1993,
            publication = "Journal of Physical Education, Recreation and Dance 64(1):88-90",
            url = "https://doi.org/10.1080/07303084.1993.10606684",
            supports = "Estimated one-rep max from a weight and a rep count (Brzycki)",
        ),
        Citation(
            key = CitationKey.WISHNOFSKY_1958,
            title = "Caloric equivalents of gained or lost weight",
            authors = "Wishnofsky M",
            year = 1958,
            publication = "The American Journal of Clinical Nutrition 6(5):542-546",
            url = "https://doi.org/10.1093/ajcn/6.5.542",
            supports = "The 7,700 kcal per kg cross-check used by the goal ETA",
        ),
        Citation(
            key = CitationKey.CDC_WEIGHT_LOSS,
            title = "Losing weight: a healthy rate of weight loss",
            authors = "Centers for Disease Control and Prevention",
            year = 2024,
            publication = "CDC Healthy Weight guidance",
            url = "https://www.cdc.gov/healthy-weight-growth/losing-weight/",
            supports = "Cited range for a goal rate of 0.5-1 kg per week",
        ),
        Citation(
            key = CitationKey.SLEEP_CONSENSUS_2015,
            title = "Recommended amount of sleep for a healthy adult: a joint consensus statement",
            authors = "Watson NF, Badr MS, Belenky G, et al.",
            year = 2015,
            publication = "Sleep 38(6):843-844",
            url = "https://doi.org/10.5665/sleep.4716",
            supports = "The seven-or-more-hours adult sleep recommendation behind the " +
                "sleep need setting",
        ),
        Citation(
            key = CitationKey.UCUM,
            title = "Unified Code for Units of Measure",
            authors = "Regenstrief Institute",
            year = 2024,
            publication = "UCUM specification",
            url = "https://ucum.org/ucum",
            supports = "Every unit code stored with a value",
            licenceNote = "Unit codes and conversion factors are used as identifiers; " +
                "attribution is in NOTICE.md.",
        ),
        Citation(
            key = CitationKey.FDA_DAILY_VALUES_2020,
            title = "Daily Value on the New Nutrition and Supplement Facts Labels",
            authors = "U.S. Food and Drug Administration",
            year = 2020,
            publication = "21 CFR 101.9(c)(8)(iv); FDA consumer update",
            url = "https://www.fda.gov/food/nutrition-facts-label/daily-value-new-nutrition-" +
                "and-supplement-facts-labels",
            supports = "The daily value each micronutrient is shown against on the panel",
            licenceNote = "A U.S. Government work in the public domain. The values and the " +
                "reference-diet note are reproduced; the FDA text is not.",
        ),
        Citation(
            key = CitationKey.IOM_MACRONUTRIENTS_2005,
            title = "Dietary Reference Intakes for energy, carbohydrate, fiber, fat, fatty " +
                "acids, cholesterol, protein, and amino acids",
            authors = "Institute of Medicine of the National Academies",
            year = 2005,
            publication = "The National Academies Press",
            url = "https://doi.org/10.17226/10490",
            supports = "Protein RDA (0.8 g/kg), the fat and carbohydrate acceptable ranges, " +
                "the fibre recommendation and a suggested target starting point",
            licenceNote = "Values are cited facts; the report text is not reproduced.",
        ),
        Citation(
            key = CitationKey.USDA_FOODDATA_CENTRAL,
            title = "USDA FoodData Central",
            authors = "U.S. Department of Agriculture, Agricultural Research Service",
            year = 2026,
            publication = "Foundation Foods 2026-04-30 and SR Legacy 2018-04",
            url = "https://fdc.nal.usda.gov/",
            supports = "The bundled food index: names, per-100 g nutrients and portions",
            licenceNote = "U.S. Government data released as CC0 1.0 (public domain " +
                "dedication). The version and licence are recorded in NOTICE.md and in the " +
                "app's food-data attribution screen.",
        ),
        Citation(
            key = CitationKey.OPEN_FOOD_FACTS,
            title = "Open Food Facts",
            authors = "Open Food Facts contributors",
            year = 2026,
            publication = "Open Food Facts database",
            url = "https://world.openfoodfacts.org/",
            supports = "A product looked up by the person, by barcode, and cached locally",
            licenceNote = "Open Database License (ODbL) 1.0. Attribution is shown before the " +
                "first lookup and on every product it supplies, and in NOTICE.md.",
        ),
        Citation(
            key = CitationKey.APP_RULE,
            title = "openHealth product rule",
            authors = "openHealth",
            year = 2026,
            publication = "docs/adr/0005-core-and-data.md",
            url = "https://github.com/nallalokeshsai/openHealth/blob/main/" +
                "docs/adr/0005-core-and-data.md",
            supports = "A rule this app defines itself (recovery thresholds, readiness " +
                "v1, safety floors, the weight trend and weekly rate)",
        ),
        Citation(
            key = CitationKey.ESH_HYPERTENSION_2023,
            title = "2023 ESH Guidelines for the management of arterial hypertension",
            authors = "Mancia G, Kreutz R, Brunström M, et al.; European Society of " +
                "Hypertension",
            year = 2023,
            publication = "Journal of Hypertension 41(12):1874-2071",
            url = "https://doi.org/10.1097/HJH.0000000000003480",
            supports = "The home blood-pressure threshold (135/85 mmHg; office 140/90) " +
                "and the home-measurement protocol (duplicate morning and evening " +
                "readings for 7 days, 3 days minimum, first day discarded)",
            licenceNote = "A published clinical threshold; no guideline text is reproduced.",
        ),
        Citation(
            key = CitationKey.AHA_RESTING_HEART_RATE,
            title = "All About Heart Rate",
            authors = "American Heart Association",
            year = 2024,
            publication = "American Heart Association health topic",
            url = "https://www.heart.org/en/health-topics/high-blood-pressure/" +
                "the-facts-about-high-blood-pressure/all-about-heart-rate-pulse",
            supports = "The 60-100 beats-per-minute resting heart rate range for adults",
            licenceNote = "A published reference range; no page text is reproduced.",
        ),
        Citation(
            key = CitationKey.WHO_PULSE_OXIMETRY_2011,
            title = "Pulse Oximetry Training Manual",
            authors = "World Health Organization",
            year = 2011,
            publication = "WHO Patient Safety Programme, Geneva",
            url = "https://www.who.int/publications/i/item/9789241501133",
            supports = "The 95-100 % oxygen-saturation range for a healthy adult at sea " +
                "level",
            licenceNote = "A published reference range; no manual text is reproduced.",
        ),
        Citation(
            key = CitationKey.GENEVA_BODY_TEMPERATURE_2019,
            title = "Normal Body Temperature: A Systematic Review",
            authors = "Geneva II, Cuzzo B, Fazili T, Javaid W",
            year = 2019,
            publication = "Open Forum Infectious Diseases 6(4):ofz032",
            url = "https://doi.org/10.1093/ofid/ofz032",
            supports = "The 36.16-37.02 degrees Celsius mean +/- 2 SD adult temperature " +
                "across measurement sites (shown as 36.2-37.0)",
            licenceNote = "A published reference range; the article text is not reproduced.",
        ),
        Citation(
            key = CitationKey.ADA_GLYCEMIC_TARGETS_2026,
            title = "2. Diagnosis and Classification of Diabetes: Standards of Care in " +
                "Diabetes-2026",
            authors = "American Diabetes Association Professional Practice Committee",
            year = 2026,
            publication = "Diabetes Care 49(Suppl 1):S27-S49",
            url = "https://doi.org/10.2337/dc26-S002",
            supports = "Fasting plasma glucose below 5.6 mmol/L and the two-hour value " +
                "below 7.8 mmol/L (Table 2.2); 3.9 mmol/L as the Level 1 low threshold " +
                "in Table 6.4 of the same standards (dc26-S006)",
            licenceNote = "Published thresholds; the standards text is not reproduced.",
        ),
    )

    private val byKey: Map<CitationKey, Citation> = entries.associateBy(Citation::key)

    fun of(key: CitationKey): Citation = byKey.getValue(key)

    fun all(): List<Citation> = entries

    /** Keys that a formula or range may use but that have no row are a programming error. */
    fun requirePresent(key: CitationKey): Citation =
        byKey[key] ?: error("Citation $key is referenced but not in the citation table")
}
