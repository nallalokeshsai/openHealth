@file:Suppress(
    "MagicNumber",
    "LargeClass",
    "LongParameterList",
    "ReturnCount",
)

package app.openhealth.core.activity

import app.openhealth.core.body.ActivityMuscleLoad
import app.openhealth.core.body.Muscle
import kotlin.time.Instant

/** How hard a pose is, in the library's own words (`pr.md` §6.7 MVP). */
enum class YogaLevel(val labelKey: String) {
    BEGINNER("yoga.level.beginner"),
    INTERMEDIATE("yoga.level.intermediate"),
    ADVANCED("yoga.level.advanced"),
}

enum class YogaPoseCategory(val labelKey: String) {
    STANDING("yoga.category.standing"),
    BALANCE("yoga.category.balance"),
    BACKBEND("yoga.category.backbend"),
    FORWARD_FOLD("yoga.category.forward_fold"),
    TWIST("yoga.category.twist"),
    SEATED("yoga.category.seated"),
    SUPINE("yoga.category.supine"),
    PRONE("yoga.category.prone"),
    REST("yoga.category.rest"),
    INVERSION("yoga.category.inversion"),
}

/** Which side a step is done on; the player names it and asks to switch. */
enum class YogaSide(val labelKey: String?) {
    NONE(null),
    LEFT("yoga.side.left"),
    RIGHT("yoga.side.right"),
    BOTH("yoga.side.both"),
}

/**
 * A pose, written for this app (`pr.md` §6.7; B11: pose names, text and body-map
 * diagrams are ours, no scraped or unlicensed media). The "why" is a plain sentence and
 * every pose carries a modification, so the library is useful without a teacher.
 */
data class YogaPose(
    val id: String,
    val name: String,
    val sanskrit: String,
    val level: YogaLevel,
    val category: YogaPoseCategory,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val setup: List<String>,
    val why: String,
    val modification: String,
    val contraindications: String? = null,
    val breathCue: String,
    val defaultHoldSeconds: Int = 30,
)

/** One ordered step of a flow. */
data class YogaStep(
    val poseId: String,
    val holdSeconds: Int,
    val side: YogaSide = YogaSide.NONE,
    val breathCue: String? = null,
    val transitionSeconds: Int = 5,
) {
    init {
        require(holdSeconds >= 3) { "A hold shorter than 3 s is not a pose" }
        require(transitionSeconds in 0..30) { "A transition longer than 30 s is not a transition" }
    }
}

data class YogaFlow(
    val id: String,
    val name: String,
    val subtitle: String,
    val level: YogaLevel,
    val focus: String,
    val steps: List<YogaStep>,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank()) { "A flow needs an id and a name" }
        require(steps.isNotEmpty()) { "A flow needs at least one step" }
    }

    val totalSeconds: Int get() = steps.sumOf { it.holdSeconds + it.transitionSeconds }
    val poseCount: Int get() = steps.size
    val minutes: Int get() = (totalSeconds + 30) / 60
}

/**
 * The built-in library. The eight flows are the MVP set from `pr.md` §6.7: Sun Salutation
 * A and B, beginner full-body, hip opener, spine, post-run, desk reset and bedtime.
 * Text and muscle assignments are ours; the body map draws each pose's muscles.
 */
object YogaLibrary {
    private fun pose(
        id: String,
        name: String,
        sanskrit: String,
        level: YogaLevel,
        category: YogaPoseCategory,
        primary: List<Muscle>,
        secondary: List<Muscle> = emptyList(),
        setup: List<String>,
        why: String,
        modification: String,
        contraindications: String? = null,
        breathCue: String,
        hold: Int = 30,
    ) = YogaPose(
        id, name, sanskrit, level, category, primary, secondary, setup, why, modification,
        contraindications, breathCue, hold,
    )

    val poses: List<YogaPose> = listOf(
        pose(
            "mountain", "Mountain", "Tadasana", YogaLevel.BEGINNER, YogaPoseCategory.STANDING,
            listOf(Muscle.ABS), listOf(Muscle.GLUTES, Muscle.CALVES),
            listOf(
                "Stand with the feet hip-width apart and the weight even.",
                "Lengthen the spine and let the arms rest beside the body.",
                "Softly draw the lower ribs down and look straight ahead.",
            ),
            "A steady base that lets you notice the breath before moving.",
            "Stand with the back against a wall for feedback, or sit tall in a chair.",
            breathCue = "Breathe in through the nose for four, out for six.",
            hold = 30,
        ),
        pose(
            "downward-dog", "Downward-facing dog", "Adho Mukha Svanasana",
            YogaLevel.BEGINNER, YogaPoseCategory.INVERSION,
            listOf(Muscle.DELTOIDS, Muscle.UPPER_BACK), listOf(Muscle.HAMSTRINGS, Muscle.CALVES),
            listOf(
                "From all fours, walk the hands one hand-length forward.",
                "Lift the hips up and back, bending the knees as much as you need.",
                "Press the floor away and let the head hang between the arms.",
            ),
            "A whole-body stretch that opens the shoulders and the back of the legs.",
            "Keep the knees bent and the heels lifted; a chair under the hands shortens the reach.",
            contraindications = "Wrists that are painful in extension: use fists or a chair.",
            breathCue = "Inhale to lengthen, exhale to lift the hips a little higher.",
            hold = 40,
        ),
        pose(
            "forward-fold", "Standing forward fold", "Uttanasana", YogaLevel.BEGINNER,
            YogaPoseCategory.FORWARD_FOLD,
            listOf(Muscle.HAMSTRINGS), listOf(Muscle.LOWER_BACK, Muscle.CALVES),
            listOf(
                "From Mountain, hinge at the hips and fold forward.",
                "Let the head and arms hang, or hold opposite elbows.",
                "Bend the knees until the back can lengthen.",
            ),
            "Releases the hamstrings and lets the spine decompress after standing.",
            "Sit on a chair and fold forward from the hips, or put the hands on blocks.",
            breathCue = "Exhale as you fold, inhale into the back of the ribs.",
            hold = 30,
        ),
        pose(
            "halfway-lift", "Halfway lift", "Ardha Uttanasana", YogaLevel.BEGINNER,
            YogaPoseCategory.FORWARD_FOLD,
            listOf(Muscle.LOWER_BACK), listOf(Muscle.HAMSTRINGS, Muscle.GLUTES),
            listOf(
                "From the forward fold, bring the hands to the shins.",
                "Lengthen the spine until the back is long and nearly flat.",
                "Keep a soft bend in the knees.",
            ),
            "Teaches the hip hinge that protects the back in the next movement.",
            "Hands on a chair seat or two blocks; keep the fold if the back rounds.",
            breathCue = "Inhale to lengthen the spine.",
            hold = 10,
        ),
        pose(
            "plank", "Plank", "Phalakasana", YogaLevel.BEGINNER, YogaPoseCategory.STANDING,
            listOf(Muscle.ABS, Muscle.DELTOIDS), listOf(Muscle.QUADS, Muscle.GLUTES),
            listOf(
                "Place the hands under the shoulders and step the feet back.",
                "Make one line from the heels through the hips to the head.",
                "Press the hands down and draw the lower belly in.",
            ),
            "Builds a stable trunk that carries into every standing pose.",
            "Drop the knees to the floor, or put the forearms on a bench.",
            contraindications = "Wrists that are painful in extension: use forearms or a bench.",
            breathCue = "Breathe steadily; a slower exhale keeps the line.",
            hold = 25,
        ),
        pose(
            "chaturanga", "Low push-up", "Chaturanga Dandasana", YogaLevel.INTERMEDIATE,
            YogaPoseCategory.STANDING,
            listOf(Muscle.TRICEPS, Muscle.CHEST), listOf(Muscle.ABS, Muscle.DELTOIDS),
            listOf(
                "From Plank, shift forward slightly and bend the elbows to the ribs.",
                "Lower until the upper arms are about level with the torso.",
                "Keep the shoulders above the elbows and the body in one line.",
            ),
            "Builds pressing strength through the arms and chest.",
            "Lower the knees first, or skip straight to Child's pose.",
            contraindications = "Shoulder pain: keep the elbows high or skip the lowering.",
            breathCue = "Exhale as you lower.",
            hold = 8,
        ),
        pose(
            "upward-dog", "Upward-facing dog", "Urdhva Mukha Svanasana",
            YogaLevel.INTERMEDIATE, YogaPoseCategory.BACKBEND,
            listOf(Muscle.LOWER_BACK, Muscle.TRICEPS), listOf(Muscle.CHEST, Muscle.GLUTES),
            listOf(
                "From the low push-up, roll over the toes and press the hands down.",
                "Lift the chest and the front of the hips; keep the legs active.",
                "Let the shoulders stay low and the neck long.",
            ),
            "Opens the front of the body after the lowering.",
            "Use Cobra with the forearms down, or Sphinx, to keep the low back easy.",
            contraindications = "Low-back sensitivity: keep the hips on the floor in Sphinx.",
            breathCue = "Inhale as the chest rises.",
            hold = 12,
        ),
        pose(
            "cobra", "Cobra", "Bhujangasana", YogaLevel.BEGINNER, YogaPoseCategory.BACKBEND,
            listOf(Muscle.LOWER_BACK), listOf(Muscle.CHEST, Muscle.GLUTES),
            listOf(
                "Lie face down with the hands under the shoulders.",
                "Press lightly and lift the chest, keeping the hips on the floor.",
                "Draw the shoulder blades back and down.",
            ),
            "A gentle backbend that balances time spent sitting.",
            "Keep the forearms down in Sphinx, or lift only the head and chest a little.",
            contraindications = "Low-back sensitivity: keep the movement small and pain-free.",
            breathCue = "Inhale to lift, exhale to stay and broaden the chest.",
            hold = 20,
        ),
        pose(
            "child", "Child's pose", "Balasana", YogaLevel.BEGINNER, YogaPoseCategory.REST,
            listOf(Muscle.LOWER_BACK), listOf(Muscle.GLUTES, Muscle.UPPER_BACK),
            listOf(
                "Kneel with the big toes together and the knees apart.",
                "Sit the hips back toward the heels and walk the hands forward.",
                "Rest the forehead on the floor or a cushion.",
            ),
            "A resting shape that lets the back lengthen between efforts.",
            "Sit on a cushion, or do the same shape with the forearms on a chair.",
            breathCue = "Let the exhale be longer than the inhale.",
            hold = 40,
        ),
        pose(
            "cat-cow", "Cat and cow", "Marjaryasana-Bitilasana", YogaLevel.BEGINNER,
            YogaPoseCategory.SEATED,
            listOf(Muscle.LOWER_BACK, Muscle.ABS), listOf(Muscle.UPPER_BACK, Muscle.NECK),
            listOf(
                "Start on all fours with the wrists under the shoulders.",
                "Inhale, drop the belly and lift the gaze for cow.",
                "Exhale, round the back and tuck the chin for cat.",
            ),
            "Warms the spine through its full range before stronger shapes.",
            "Do the same movement seated in a chair, moving the chest and the pelvis.",
            breathCue = "Inhale to arch, exhale to round.",
            hold = 40,
        ),
        pose(
            "warrior-one", "Warrior one", "Virabhadrasana I", YogaLevel.BEGINNER,
            YogaPoseCategory.STANDING,
            listOf(Muscle.QUADS, Muscle.GLUTES), listOf(Muscle.HIP_FLEXORS, Muscle.ABS),
            listOf(
                "Step one foot back and turn it out about 45 degrees.",
                "Bend the front knee over the ankle and square the hips forward.",
                "Reach the arms up and lengthen the back leg.",
            ),
            "A strong standing shape for the legs and the front of the hips.",
            "Shorten the stance and keep the back heel lifted; hands on the hips.",
            breathCue = "Inhale to reach up, exhale to settle into the front leg.",
            hold = 35,
        ),
        pose(
            "warrior-two", "Warrior two", "Virabhadrasana II", YogaLevel.BEGINNER,
            YogaPoseCategory.STANDING,
            listOf(Muscle.QUADS, Muscle.GLUTES), listOf(Muscle.ADDUCTORS, Muscle.ABS),
            listOf(
                "From Warrior one, open the hips and the arms to the sides.",
                "Bend the front knee toward a right angle, tracking over the toes.",
                "Look over the front hand and keep both sides of the waist long.",
            ),
            "Builds leg endurance and an open, steady stance.",
            "Shorten the stance or practise with the back against a wall.",
            breathCue = "Breathe out as you sink a little lower.",
            hold = 35,
        ),
        pose(
            "triangle", "Triangle", "Utthita Trikonasana", YogaLevel.BEGINNER,
            YogaPoseCategory.STANDING,
            listOf(Muscle.HAMSTRINGS, Muscle.OBLIQUES), listOf(Muscle.QUADS, Muscle.GLUTES),
            listOf(
                "Stand with the feet wide and turn the front foot out.",
                "Reach the front hand forward and tip from the hip.",
                "Rest the hand on the shin or a block and open the top arm up.",
            ),
            "Stretches the side of the body and the back of the front leg.",
            "Keep the bottom hand on a block and shorten the stance; do not force the side bend.",
            breathCue = "Inhale to reach, exhale to tip.",
            hold = 30,
        ),
        pose(
            "tree", "Tree", "Vrksasana", YogaLevel.BEGINNER, YogaPoseCategory.BALANCE,
            listOf(Muscle.GLUTES, Muscle.CALVES), listOf(Muscle.ABS, Muscle.ADDUCTORS),
            listOf(
                "From Mountain, root the standing foot and lift the other foot.",
                "Place the sole on the calf or the inner thigh, never on the knee.",
                "Bring the hands together at the chest and find a still point to look at.",
            ),
            "Trains balance and the small stabilising muscles of the standing leg.",
            "Keep the toes on the floor, or hold a wall or chair with one hand.",
            breathCue = "Steady, even breaths; let the gaze rest.",
            hold = 30,
        ),
        pose(
            "chair", "Chair", "Utkatasana", YogaLevel.BEGINNER, YogaPoseCategory.STANDING,
            listOf(Muscle.QUADS, Muscle.GLUTES), listOf(Muscle.CALVES, Muscle.ABS),
            listOf(
                "Stand with the feet together or hip-width apart.",
                "Bend the knees and sit the hips back as if toward a chair.",
                "Reach the arms up and keep the chest lifted.",
            ),
            "Builds strength in the legs with a shape that fits a small space.",
            "Do it with the back sliding down a wall, or sit back into a real chair.",
            breathCue = "Exhale as you sit down, inhale to lift the chest.",
            hold = 25,
        ),
        pose(
            "bridge", "Bridge", "Setu Bandha Sarvangasana", YogaLevel.BEGINNER,
            YogaPoseCategory.BACKBEND,
            listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), listOf(Muscle.LOWER_BACK, Muscle.ABS),
            listOf(
                "Lie on the back with the knees bent and the feet hip-width apart.",
                "Press the feet down and lift the hips.",
                "Keep the ribs soft and the knees pointing forward.",
            ),
            "Strengthens the glutes and opens the front of the hips.",
            "Lift only a little, or place a block under the hips for support.",
            breathCue = "Inhale at the bottom, exhale as the hips lift.",
            hold = 30,
        ),
        pose(
            "supine-twist", "Supine twist", "Supta Matsyendrasana", YogaLevel.BEGINNER,
            YogaPoseCategory.TWIST,
            listOf(Muscle.OBLIQUES, Muscle.LOWER_BACK), listOf(Muscle.GLUTES),
            listOf(
                "Lie on the back and draw the knees toward the chest.",
                "Let both knees fall to one side and turn the head the other way.",
                "Keep both shoulders resting on the floor.",
            ),
            "A gentle rotation that helps the back settle after activity.",
            "Place a cushion under the knees, or keep the feet on the floor and drop one knee.",
            breathCue = "Exhale as the knees fall; breathe into the side ribs.",
            hold = 40,
        ),
        pose(
            "pigeon", "Pigeon", "Eka Pada Rajakapotasana", YogaLevel.INTERMEDIATE,
            YogaPoseCategory.SEATED,
            listOf(Muscle.GLUTES, Muscle.HIP_FLEXORS), listOf(Muscle.LOWER_BACK),
            listOf(
                "From all fours, bring one knee forward and the shin across the mat.",
                "Slide the other leg back and let the hips settle square.",
                "Walk the hands forward until you feel a stretch, not a pinch.",
            ),
            "Opens the hip of the front leg and the front of the back hip.",
            "Use a cushion under the front hip, or do the seated figure-four on a chair.",
            contraindications = "Knee pain: keep the front shin close to the body or skip it.",
            breathCue = "Long, slow exhales; let the hips drop a little on each one.",
            hold = 45,
        ),
        pose(
            "seated-forward-fold", "Seated forward fold", "Paschimottanasana",
            YogaLevel.BEGINNER, YogaPoseCategory.FORWARD_FOLD,
            listOf(Muscle.HAMSTRINGS), listOf(Muscle.LOWER_BACK, Muscle.CALVES),
            listOf(
                "Sit with the legs forward and the feet flexed.",
                "Hinge from the hips and reach toward the feet or the shins.",
                "Keep the spine long rather than pulling with the arms.",
            ),
            "Stretches the back of the legs and the back of the spine.",
            "Bend the knees generously, or loop a strap around the feet and sit on a cushion.",
            breathCue = "Inhale to lengthen, exhale to fold a little more.",
            hold = 40,
        ),
        pose(
            "butterfly", "Butterfly", "Baddha Konasana", YogaLevel.BEGINNER,
            YogaPoseCategory.SEATED,
            listOf(Muscle.ADDUCTORS), listOf(Muscle.GLUTES, Muscle.LOWER_BACK),
            listOf(
                "Sit with the soles of the feet together and the knees apart.",
                "Hold the feet and sit tall, or fold forward from the hips.",
                "Let the knees be where they are; do not press them down.",
            ),
            "Opens the inner thighs and gives the hips a gentle shape.",
            "Sit on a cushion, or lie on the back with the soles together and the knees supported.",
            breathCue = "Exhale and let the inner thighs soften.",
            hold = 40,
        ),
        pose(
            "happy-baby", "Happy baby", "Ananda Balasana", YogaLevel.BEGINNER,
            YogaPoseCategory.SUPINE,
            listOf(Muscle.HIP_FLEXORS, Muscle.LOWER_BACK), listOf(Muscle.ADDUCTORS, Muscle.ABS),
            listOf(
                "Lie on the back and draw the knees toward the armpits.",
                "Hold the outside of the feet or the backs of the thighs.",
                "Rock gently side to side.",
            ),
            "Releases the hips and the lower back with very little effort.",
            "Hold the backs of the thighs instead of the feet, or keep one foot on the floor.",
            breathCue = "Breathe into the belly and let the hips soften.",
            hold = 35,
        ),
        pose(
            "legs-up-wall", "Legs up the wall", "Viparita Karani", YogaLevel.BEGINNER,
            YogaPoseCategory.INVERSION,
            listOf(Muscle.HAMSTRINGS), listOf(Muscle.CALVES, Muscle.LOWER_BACK),
            listOf(
                "Sit sideways close to a wall and swing the legs up.",
                "Let the hips rest a little away from the wall and the arms open.",
                "Stay warm and keep the neck neutral.",
            ),
            "A restful shape that lets the legs drain after a run or a long day.",
            "Put the lower legs on a chair seat instead of the wall.",
            breathCue = "Slow, quiet breaths; let the legs be heavy.",
            hold = 90,
        ),
        pose(
            "corpse", "Corpse", "Savasana", YogaLevel.BEGINNER, YogaPoseCategory.REST,
            listOf(Muscle.ABS), listOf(Muscle.LOWER_BACK),
            listOf(
                "Lie on the back and let the feet fall open.",
                "Rest the arms a little away from the body with the palms up.",
                "Let the whole body be supported by the floor.",
            ),
            "A deliberate rest that closes the session and lets the breath settle.",
            "Lie with the knees supported on a bolster, or sit and rest in a chair.",
            breathCue = "Let the breath be natural; no count is needed.",
            hold = 120,
        ),
        pose(
            "low-lunge", "Low lunge", "Anjaneyasana", YogaLevel.BEGINNER,
            YogaPoseCategory.STANDING,
            listOf(Muscle.HIP_FLEXORS, Muscle.QUADS), listOf(Muscle.GLUTES, Muscle.ABS),
            listOf(
                "Step one foot forward and lower the back knee.",
                "Keep the front knee over the ankle and the hips level.",
                "Reach the arms up or keep the hands on the front thigh.",
            ),
            "Opens the front of the back hip, which shortens when sitting.",
            "Put a cushion under the back knee and keep the hands on blocks.",
            breathCue = "Inhale to lift the chest, exhale to sink a little.",
            hold = 35,
        ),
        pose(
            "side-plank", "Side plank", "Vasisthasana", YogaLevel.INTERMEDIATE,
            YogaPoseCategory.BALANCE,
            listOf(Muscle.OBLIQUES, Muscle.DELTOIDS), listOf(Muscle.GLUTES, Muscle.ABS),
            listOf(
                "From Plank, roll onto one hand and stack the feet or stagger them.",
                "Lift the hips and reach the top arm up.",
                "Keep the supporting wrist under the shoulder.",
            ),
            "Trains the side of the trunk and the shoulder that stabilises it.",
            "Bend the bottom knee to the floor, or do it with the forearm down.",
            contraindications = "Wrist or shoulder pain: use the forearm and a shorter hold.",
            breathCue = "Steady breaths; exhale to lift the hips.",
            hold = 20,
        ),
        pose(
            "boat", "Boat", "Navasana", YogaLevel.INTERMEDIATE, YogaPoseCategory.SEATED,
            listOf(Muscle.ABS, Muscle.HIP_FLEXORS), listOf(Muscle.QUADS),
            listOf(
                "Sit with the knees bent and the feet on the floor.",
                "Lean back slightly and lift the feet, keeping the chest open.",
                "Straighten the legs only as far as the back stays long.",
            ),
            "Builds the deep trunk muscles that hold a long run together.",
            "Keep the feet on the floor and lift only the chest, or hold behind the thighs.",
            breathCue = "Exhale to lift, breathe steadily while you hold.",
            hold = 20,
        ),
        pose(
            "camel", "Camel", "Ustrasana", YogaLevel.INTERMEDIATE, YogaPoseCategory.BACKBEND,
            listOf(Muscle.LOWER_BACK, Muscle.QUADS), listOf(Muscle.CHEST, Muscle.GLUTES),
            listOf(
                "Kneel with the knees hip-width apart and the hands on the hips.",
                "Lift the chest and lean back, keeping the hips over the knees.",
                "Reach for the heels only if the low back stays comfortable.",
            ),
            "Opens the front of the body after a day of sitting or riding.",
            "Keep the hands on the hips and the chin slightly tucked; do not reach for the heels.",
            contraindications = "Low-back or neck sensitivity: keep the backbend " +
                "small or use a wall.",
            breathCue = "Inhale to lift the chest, exhale to stay.",
            hold = 20,
        ),
        pose(
            "sphinx", "Sphinx", "Salamba Bhujangasana", YogaLevel.BEGINNER,
            YogaPoseCategory.BACKBEND,
            listOf(Muscle.LOWER_BACK), listOf(Muscle.CHEST, Muscle.ABS),
            listOf(
                "Lie face down and prop the forearms under the shoulders.",
                "Press the forearms down and lift the chest.",
                "Keep the hips and the tops of the feet on the floor.",
            ),
            "A supported backbend that is easier on the wrists than Cobra.",
            "Put a cushion under the chest, or keep the movement very small.",
            breathCue = "Inhale to lengthen, exhale to broaden the collarbones.",
            hold = 30,
        ),
        pose(
            "seated-twist", "Seated twist", "Ardha Matsyendrasana", YogaLevel.BEGINNER,
            YogaPoseCategory.TWIST,
            listOf(Muscle.OBLIQUES, Muscle.LOWER_BACK), listOf(Muscle.NECK, Muscle.UPPER_BACK),
            listOf(
                "Sit tall with both legs forward, then bend one knee across.",
                "Turn the chest toward the bent knee and place a hand behind you.",
                "Grow taller on each inhale and turn a little more on the exhale.",
            ),
            "Restores rotation through the spine after time in one position.",
            "Do it in a chair with the feet on the floor and one hand on the chair back.",
            breathCue = "Inhale tall, exhale to turn.",
            hold = 35,
        ),
        pose(
            "neck-release", "Neck release", "Greeva Sanchalana", YogaLevel.BEGINNER,
            YogaPoseCategory.SEATED,
            listOf(Muscle.NECK, Muscle.TRAPEZIUS), emptyList(),
            listOf(
                "Sit tall and let the right ear fall toward the right shoulder.",
                "Keep the left shoulder heavy and breathe into the left side of the neck.",
                "Change sides slowly, without rolling the head back.",
            ),
            "Eases the neck after a long stretch at a desk.",
            "Do the same movement seated, with no stretch at all, just a gentle tilt.",
            breathCue = "Breathe into the long side of the neck.",
            hold = 25,
        ),
    )

    val flows: List<YogaFlow> = listOf(
        YogaFlow(
            id = "sun-salutation-a",
            name = "Sun Salutation A",
            subtitle = "A steady round of the classic sequence.",
            level = YogaLevel.BEGINNER,
            focus = "Whole body",
            steps = listOf(
                YogaStep("mountain", 20, breathCue = "Stand tall and breathe in."),
                YogaStep("forward-fold", 20),
                YogaStep("halfway-lift", 10),
                YogaStep("plank", 15),
                YogaStep("chaturanga", 6),
                YogaStep("upward-dog", 10),
                YogaStep("downward-dog", 30),
                YogaStep("halfway-lift", 10),
                YogaStep("forward-fold", 20),
                YogaStep("mountain", 20, breathCue = "Return to standing."),
            ),
        ),
        YogaFlow(
            id = "sun-salutation-b",
            name = "Sun Salutation B",
            subtitle = "The longer salutation with a chair and a warrior.",
            level = YogaLevel.INTERMEDIATE,
            focus = "Strength and flow",
            steps = listOf(
                YogaStep("mountain", 15),
                YogaStep("chair", 20),
                YogaStep("forward-fold", 15),
                YogaStep("halfway-lift", 8),
                YogaStep("plank", 12),
                YogaStep("chaturanga", 6),
                YogaStep("upward-dog", 8),
                YogaStep("downward-dog", 25),
                YogaStep("warrior-one", 25, side = YogaSide.RIGHT),
                YogaStep("warrior-one", 25, side = YogaSide.LEFT),
                YogaStep("downward-dog", 20),
                YogaStep("plank", 12),
                YogaStep("chaturanga", 6),
                YogaStep("upward-dog", 8),
                YogaStep("downward-dog", 20),
                YogaStep("mountain", 15),
            ),
        ),
        YogaFlow(
            id = "beginner-full-body",
            name = "Beginner full body",
            subtitle = "A first flow that touches every part of the body.",
            level = YogaLevel.BEGINNER,
            focus = "Whole body",
            steps = listOf(
                YogaStep("mountain", 20),
                YogaStep("cat-cow", 40),
                YogaStep("downward-dog", 30),
                YogaStep("warrior-one", 30, side = YogaSide.LEFT),
                YogaStep("warrior-one", 30, side = YogaSide.RIGHT),
                YogaStep("warrior-two", 30, side = YogaSide.LEFT),
                YogaStep("warrior-two", 30, side = YogaSide.RIGHT),
                YogaStep("triangle", 25, side = YogaSide.LEFT),
                YogaStep("triangle", 25, side = YogaSide.RIGHT),
                YogaStep("bridge", 30),
                YogaStep("supine-twist", 40, side = YogaSide.LEFT),
                YogaStep("supine-twist", 40, side = YogaSide.RIGHT),
                YogaStep("corpse", 60),
            ),
        ),
        YogaFlow(
            id = "hip-opener",
            name = "Hip opener",
            subtitle = "For hips that have been sitting all day.",
            level = YogaLevel.BEGINNER,
            focus = "Hips",
            steps = listOf(
                YogaStep("cat-cow", 40),
                YogaStep("low-lunge", 30, side = YogaSide.LEFT),
                YogaStep("low-lunge", 30, side = YogaSide.RIGHT),
                YogaStep("warrior-two", 30, side = YogaSide.LEFT),
                YogaStep("warrior-two", 30, side = YogaSide.RIGHT),
                YogaStep("pigeon", 40, side = YogaSide.LEFT),
                YogaStep("pigeon", 40, side = YogaSide.RIGHT),
                YogaStep("butterfly", 40),
                YogaStep("happy-baby", 35),
                YogaStep("supine-twist", 40, side = YogaSide.LEFT),
                YogaStep("supine-twist", 40, side = YogaSide.RIGHT),
            ),
        ),
        YogaFlow(
            id = "spine",
            name = "Spine",
            subtitle = "Flexion, extension and rotation in a gentle order.",
            level = YogaLevel.BEGINNER,
            focus = "Back",
            steps = listOf(
                YogaStep("cat-cow", 40),
                YogaStep("sphinx", 30),
                YogaStep("cobra", 20),
                YogaStep("child", 40),
                YogaStep("seated-twist", 35, side = YogaSide.LEFT),
                YogaStep("seated-twist", 35, side = YogaSide.RIGHT),
                YogaStep("camel", 20),
                YogaStep("supine-twist", 40, side = YogaSide.LEFT),
                YogaStep("supine-twist", 40, side = YogaSide.RIGHT),
                YogaStep("corpse", 60),
            ),
        ),
        YogaFlow(
            id = "post-run",
            name = "Post-run",
            subtitle = "A short reset for legs that have just finished.",
            level = YogaLevel.BEGINNER,
            focus = "Legs",
            steps = listOf(
                YogaStep("forward-fold", 30),
                YogaStep("low-lunge", 35, side = YogaSide.LEFT),
                YogaStep("low-lunge", 35, side = YogaSide.RIGHT),
                YogaStep("pigeon", 45, side = YogaSide.LEFT),
                YogaStep("pigeon", 45, side = YogaSide.RIGHT),
                YogaStep("downward-dog", 30),
                YogaStep("legs-up-wall", 60),
                YogaStep("supine-twist", 40, side = YogaSide.LEFT),
                YogaStep("supine-twist", 40, side = YogaSide.RIGHT),
            ),
        ),
        YogaFlow(
            id = "desk-reset",
            name = "Desk reset",
            subtitle = "Five minutes that undo a long meeting.",
            level = YogaLevel.BEGINNER,
            focus = "Neck, shoulders and hips",
            steps = listOf(
                YogaStep("mountain", 20),
                YogaStep("neck-release", 25, side = YogaSide.LEFT),
                YogaStep("neck-release", 25, side = YogaSide.RIGHT),
                YogaStep("cat-cow", 30),
                YogaStep("seated-twist", 30, side = YogaSide.LEFT),
                YogaStep("seated-twist", 30, side = YogaSide.RIGHT),
                YogaStep("low-lunge", 30, side = YogaSide.LEFT),
                YogaStep("low-lunge", 30, side = YogaSide.RIGHT),
                YogaStep("forward-fold", 25),
                YogaStep("child", 40),
            ),
        ),
        YogaFlow(
            id = "bedtime",
            name = "Bedtime",
            subtitle = "Slow shapes to close the day.",
            level = YogaLevel.BEGINNER,
            focus = "Rest",
            steps = listOf(
                YogaStep("child", 45),
                YogaStep("butterfly", 40),
                YogaStep("seated-forward-fold", 40),
                YogaStep("supine-twist", 45, side = YogaSide.LEFT),
                YogaStep("supine-twist", 45, side = YogaSide.RIGHT),
                YogaStep("happy-baby", 35),
                YogaStep("legs-up-wall", 90),
                YogaStep("corpse", 120),
            ),
        ),
    )

    private val poseIndex = poses.associateBy { it.id }
    private val flowIndex = flows.associateBy { it.id }

    fun pose(id: String): YogaPose? = poseIndex[id]
    fun flow(id: String): YogaFlow? = flowIndex[id]

    fun byCategory(category: YogaPoseCategory): List<YogaPose> =
        poses.filter { it.category == category }

    /** A flow is valid only when every step names a pose that exists. */
    fun validate(): List<String> = buildList {
        poses.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            add("duplicate pose id $it")
        }
        flows.forEach { flow ->
            flow.steps.forEach { step ->
                if (pose(step.poseId) == null) add("${flow.id} names missing pose ${step.poseId}")
            }
        }
    }
}

/**
 * Muscle loading for a yoga session: every held step adds its pose's primary muscles at
 * full weight and secondary muscles at half, scaled by the hold relative to the library's
 * default. The result feeds the recovery map and the session summary.
 */
data class YogaMuscleLoad(
    val weights: Map<Muscle, Double>,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
) {
    val isEmpty: Boolean get() = weights.isEmpty()
}

object YogaLoad {
    fun of(flow: YogaFlow, held: Map<String, Int> = emptyMap()): YogaMuscleLoad {
        val weights = mutableMapOf<Muscle, Double>()
        flow.steps.forEach { step ->
            val pose = YogaLibrary.pose(step.poseId) ?: return@forEach
            val hold = (held[step.poseId] ?: step.holdSeconds).toDouble()
            val scale = (hold / pose.defaultHoldSeconds.coerceAtLeast(1)).coerceIn(0.25, 3.0)
            pose.primaryMuscles.forEach { muscle ->
                weights[muscle] = (weights[muscle] ?: 0.0) + scale
            }
            pose.secondaryMuscles.forEach { muscle ->
                weights[muscle] = (weights[muscle] ?: 0.0) + scale * Muscle.SECONDARY_WEIGHT
            }
        }
        val sorted = weights.entries.sortedByDescending { it.value }.map { it.key }
        return YogaMuscleLoad(
            weights = weights,
            primary = sorted.take(4),
            secondary = sorted.drop(4),
        )
    }

    fun loadOf(flow: YogaFlow, at: Instant, activityId: String): ActivityMuscleLoad =
        ActivityMuscleLoad(
            activityId = activityId,
            at = at,
            primary = of(flow).primary,
            secondary = of(flow).secondary,
        )
}

/**
 * The breath pacer used by the player (`pr.md` §6.7: breathing). Four phases, each in
 * seconds; a count of zero skips that phase.
 */
data class BreathPattern(
    val inhale: Int = 4,
    val holdIn: Int = 0,
    val exhale: Int = 6,
    val holdOut: Int = 0,
) {
    init {
        require(inhale >= 0 && holdIn >= 0 && exhale >= 0 && holdOut >= 0) {
            "Breath phases must not be negative"
        }
        require(inhale + holdIn + exhale + holdOut > 0) { "A breath needs at least one phase" }
    }

    val cycleSeconds: Int get() = inhale + holdIn + exhale + holdOut

    fun phaseAt(second: Int): BreathPhase {
        var cursor = ((second % cycleSeconds) + cycleSeconds) % cycleSeconds
        if (cursor < inhale) return BreathPhase.INHALE
        cursor -= inhale
        if (cursor < holdIn) return BreathPhase.HOLD_IN
        cursor -= holdIn
        if (cursor < exhale) return BreathPhase.EXHALE
        return BreathPhase.HOLD_OUT
    }

    fun remainingInPhase(second: Int): Int {
        var cursor = ((second % cycleSeconds) + cycleSeconds) % cycleSeconds
        return when (phaseAt(second)) {
            BreathPhase.INHALE -> inhale - cursor
            BreathPhase.HOLD_IN -> holdIn - (cursor - inhale)
            BreathPhase.EXHALE -> exhale - (cursor - inhale - holdIn)
            BreathPhase.HOLD_OUT -> holdOut - (cursor - inhale - holdIn - exhale)
        }
    }

    companion object {
        val CALM = BreathPattern(inhale = 4, exhale = 6)
        val BOX = BreathPattern(inhale = 4, holdIn = 4, exhale = 4, holdOut = 4)
        val EXTENDED_EXHALE = BreathPattern(inhale = 4, exhale = 8)
    }
}

enum class BreathPhase(val labelKey: String) {
    INHALE("yoga.breath.inhale"),
    HOLD_IN("yoga.breath.hold_in"),
    EXHALE("yoga.breath.exhale"),
    HOLD_OUT("yoga.breath.hold_out"),
}
