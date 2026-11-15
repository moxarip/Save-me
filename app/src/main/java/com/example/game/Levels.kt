package com.example.game

object Levels {

    val all: List<LevelData> = listOf(
        // 1-4: Basic dome shields over flat ground and single beehives
        LevelData(
            id = 1,
            title = "البداية السهلة",
            subtitle = "ارسم قوساً فوق البطل لتحميه من النحل",
            heroStart = Vec2(210f, 566f),
            platforms = listOf(
                Platform(30f, 590f, 360f, 60f, PlatformStyle.GRASS)
            ),
            beehives = listOf(
                BeehiveSpec(x = 320f, y = 175f, beeCount = 12)
            ),
            maxInkLength = 760f,
            skyTheme = SkyTheme.SUNNY_MEADOW,
            hintPath = listOf(
                Vec2(162f, 584f),
                Vec2(162f, 532f),
                Vec2(210f, 515f),
                Vec2(258f, 532f),
                Vec2(258f, 584f)
            )
        ),
        LevelData(
            id = 2,
            title = "الهجوم المباشر",
            subtitle = "الخلية فوقك مباشرة! أغلق السقف جيداً",
            heroStart = Vec2(210f, 516f),
            platforms = listOf(
                Platform(115f, 540f, 190f, 44f, PlatformStyle.GRASS),
                Platform(20f, 620f, 380f, 50f, PlatformStyle.STONE)
            ),
            beehives = listOf(
                BeehiveSpec(x = 210f, y = 165f, beeCount = 14)
            ),
            maxInkLength = 780f,
            skyTheme = SkyTheme.SUNNY_MEADOW,
            hintPath = listOf(
                Vec2(160f, 534f),
                Vec2(162f, 478f),
                Vec2(210f, 464f),
                Vec2(258f, 478f),
                Vec2(260f, 534f)
            )
        ),
        LevelData(
            id = 3,
            title = "الممر الضيق",
            subtitle = "استفد من الجدارين لإغلاق الفتحة بأقل حبر",
            heroStart = Vec2(210f, 566f),
            platforms = listOf(
                Platform(40f, 590f, 340f, 55f, PlatformStyle.GRASS),
                Platform(95f, 475f, 42f, 115f, PlatformStyle.STONE),
                Platform(283f, 475f, 42f, 115f, PlatformStyle.STONE)
            ),
            beehives = listOf(
                BeehiveSpec(x = 100f, y = 180f, beeCount = 15)
            ),
            maxInkLength = 650f,
            skyTheme = SkyTheme.SUNNY_MEADOW,
            hintPath = listOf(
                Vec2(110f, 469f),
                Vec2(210f, 462f),
                Vec2(310f, 469f)
            )
        ),
        LevelData(
            id = 4,
            title = "الحافة المرتفعة",
            subtitle = "احمِ البطل من الجوانب حتى لا يتسلل النحل",
            heroStart = Vec2(210f, 486f),
            platforms = listOf(
                Platform(135f, 510f, 150f, 34f, PlatformStyle.WOOD_BRIDGE),
                Platform(30f, 625f, 360f, 50f, PlatformStyle.GRASS)
            ),
            beehives = listOf(
                BeehiveSpec(x = 325f, y = 195f, beeCount = 16)
            ),
            maxInkLength = 820f,
            skyTheme = SkyTheme.SUNNY_MEADOW,
            hintPath = listOf(
                Vec2(158f, 504f),
                Vec2(162f, 448f),
                Vec2(210f, 434f),
                Vec2(258f, 448f),
                Vec2(262f, 504f)
            )
        ),

        // 5-8: Bridging over lava pits (hook onto terrain edges so shield doesn't sink into lava)
        LevelData(
            id = 5,
            title = "فوق الحمم",
            subtitle = "علّق الخط على الحواف حتى لا يسقط في الحمم!",
            heroStart = Vec2(210f, 526f),
            platforms = listOf(
                Platform(20f, 540f, 95f, 100f, PlatformStyle.STONE),
                Platform(155f, 550f, 110f, 32f, PlatformStyle.STONE),
                Platform(305f, 540f, 95f, 100f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 210f, y = 175f, beeCount = 16)
            ),
            maxInkLength = 850f,
            skyTheme = SkyTheme.VOLCANIC_SUNSET,
            hintPath = listOf(
                Vec2(162f, 544f),
                Vec2(164f, 488f),
                Vec2(210f, 474f),
                Vec2(256f, 488f),
                Vec2(258f, 544f)
            )
        ),
        LevelData(
            id = 6,
            title = "الجسر المعلق",
            subtitle = "ثبّت الدرع على الصخرتين فوق فوهة البركان",
            heroStart = Vec2(210f, 516f),
            platforms = listOf(
                Platform(35f, 495f, 80f, 140f, PlatformStyle.STONE),
                Platform(168f, 540f, 84f, 26f, PlatformStyle.WOOD_BRIDGE),
                Platform(305f, 495f, 80f, 140f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 115f, y = 175f, beeCount = 17)
            ),
            maxInkLength = 880f,
            skyTheme = SkyTheme.VOLCANIC_SUNSET,
            hintPath = listOf(
                Vec2(95f, 489f),
                Vec2(150f, 468f),
                Vec2(210f, 462f),
                Vec2(270f, 468f),
                Vec2(325f, 489f)
            )
        ),
        LevelData(
            id = 7,
            title = "حفرة البركان",
            subtitle = "الجزيرة صغيرة! لف الخط حول حافتها كي لا ينزلق",
            heroStart = Vec2(210f, 496f),
            platforms = listOf(
                Platform(160f, 520f, 100f, 28f, PlatformStyle.STONE),
                Platform(18f, 595f, 70f, 70f, PlatformStyle.STONE),
                Platform(332f, 595f, 70f, 70f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 315f, y = 180f, beeCount = 18)
            ),
            maxInkLength = 880f,
            skyTheme = SkyTheme.VOLCANIC_SUNSET,
            hintPath = listOf(
                Vec2(154f, 554f),
                Vec2(166f, 514f),
                Vec2(166f, 456f),
                Vec2(210f, 444f),
                Vec2(254f, 456f),
                Vec2(254f, 514f),
                Vec2(266f, 554f)
            )
        ),
        LevelData(
            id = 8,
            title = "القوس المحكم",
            subtitle = "اربط بين المنصتين لتحمي البطل من الأسفل والأعلى",
            heroStart = Vec2(135f, 516f),
            platforms = listOf(
                Platform(75f, 540f, 120f, 30f, PlatformStyle.STONE),
                Platform(255f, 480f, 110f, 34f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 305f, y = 185f, beeCount = 18)
            ),
            maxInkLength = 900f,
            skyTheme = SkyTheme.VOLCANIC_SUNSET,
            hintPath = listOf(
                Vec2(88f, 534f),
                Vec2(92f, 476f),
                Vec2(135f, 464f),
                Vec2(180f, 476f),
                Vec2(184f, 534f)
            )
        ),

        // 9-12: Dual beehives attacking from left and right simultaneously
        LevelData(
            id = 9,
            title = "كماشة النحل",
            subtitle = "هجوم من اليمين واليسار معاً! أغلق كلا الجانبين",
            heroStart = Vec2(210f, 566f),
            platforms = listOf(
                Platform(25f, 590f, 370f, 55f, PlatformStyle.GRASS)
            ),
            beehives = listOf(
                BeehiveSpec(x = 75f, y = 210f, beeCount = 10),
                BeehiveSpec(x = 345f, y = 210f, beeCount = 10)
            ),
            maxInkLength = 860f,
            skyTheme = SkyTheme.TWILIGHT_SWARM,
            hintPath = listOf(
                Vec2(160f, 584f),
                Vec2(162f, 530f),
                Vec2(210f, 515f),
                Vec2(258f, 530f),
                Vec2(260f, 584f)
            )
        ),
        LevelData(
            id = 10,
            title = "هجوم مزدوج",
            subtitle = "خليتان فوق بركة الحمم — أحكم الإغلاق على المنصة",
            heroStart = Vec2(210f, 506f),
            platforms = listOf(
                Platform(135f, 530f, 150f, 34f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 80f, y = 175f, beeCount = 10),
                BeehiveSpec(x = 340f, y = 175f, beeCount = 10)
            ),
            maxInkLength = 880f,
            skyTheme = SkyTheme.TWILIGHT_SWARM,
            hintPath = listOf(
                Vec2(158f, 524f),
                Vec2(162f, 466f),
                Vec2(210f, 452f),
                Vec2(258f, 466f),
                Vec2(262f, 524f)
            )
        ),
        LevelData(
            id = 11,
            title = "الحصن المنيع",
            subtitle = "سد المداخل بين الأعمدة أمام السربين",
            heroStart = Vec2(210f, 566f),
            platforms = listOf(
                Platform(40f, 590f, 340f, 55f, PlatformStyle.STONE),
                Platform(110f, 495f, 36f, 95f, PlatformStyle.STONE),
                Platform(274f, 495f, 36f, 95f, PlatformStyle.STONE)
            ),
            beehives = listOf(
                BeehiveSpec(x = 65f, y = 320f, beeCount = 11),
                BeehiveSpec(x = 355f, y = 320f, beeCount = 11)
            ),
            maxInkLength = 760f,
            skyTheme = SkyTheme.TWILIGHT_SWARM,
            hintPath = listOf(
                Vec2(124f, 489f),
                Vec2(210f, 480f),
                Vec2(296f, 489f)
            )
        ),
        LevelData(
            id = 12,
            title = "المثلث الخطر",
            subtitle = "خلية علوية وخلية جانبية مع حمم على اليمين!",
            heroStart = Vec2(165f, 526f),
            platforms = listOf(
                Platform(40f, 550f, 200f, 42f, PlatformStyle.GRASS)
            ),
            lavaPits = listOf(
                LavaPit(240f, 665f, 180f, 95f),
                LavaPit(0f, 665f, 40f, 95f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 85f, y = 175f, beeCount = 11),
                BeehiveSpec(x = 355f, y = 390f, beeCount = 11)
            ),
            maxInkLength = 880f,
            skyTheme = SkyTheme.TWILIGHT_SWARM,
            hintPath = listOf(
                Vec2(115f, 544f),
                Vec2(118f, 486f),
                Vec2(165f, 474f),
                Vec2(212f, 486f),
                Vec2(215f, 544f)
            )
        ),

        // 13-16: Overhead falling boulders + beehives + narrow ledges
        LevelData(
            id = 13,
            title = "صخرة السماء",
            subtitle = "ارسم سقفاً مائلاً ليبعد الصخرة ويصد النحل!",
            heroStart = Vec2(210f, 566f),
            platforms = listOf(
                Platform(30f, 590f, 360f, 55f, PlatformStyle.STONE)
            ),
            beehives = listOf(
                BeehiveSpec(x = 330f, y = 185f, beeCount = 16)
            ),
            boulders = listOf(
                BoulderSpec(x = 210f, y = 140f, radius = 24f, initialVx = 18f, delaySeconds = 0.25f)
            ),
            maxInkLength = 920f,
            skyTheme = SkyTheme.ROCKY_CANYON,
            hintPath = listOf(
                Vec2(155f, 584f),
                Vec2(162f, 520f),
                Vec2(210f, 495f),
                Vec2(258f, 520f),
                Vec2(265f, 584f)
            )
        ),
        LevelData(
            id = 14,
            title = "انهيار صخري",
            subtitle = "صخرتان تسقطان من الأعلى — ابْنِ خيمة مثلثة متينة",
            heroStart = Vec2(210f, 546f),
            platforms = listOf(
                Platform(95f, 570f, 230f, 40f, PlatformStyle.STONE)
            ),
            beehives = listOf(
                BeehiveSpec(x = 80f, y = 190f, beeCount = 18)
            ),
            boulders = listOf(
                BoulderSpec(x = 175f, y = 120f, radius = 21f, initialVx = -15f, delaySeconds = 0.2f),
                BoulderSpec(x = 245f, y = 120f, radius = 21f, initialVx = 15f, delaySeconds = 0.45f)
            ),
            maxInkLength = 940f,
            skyTheme = SkyTheme.ROCKY_CANYON,
            hintPath = listOf(
                Vec2(152f, 564f),
                Vec2(160f, 500f),
                Vec2(210f, 472f),
                Vec2(260f, 500f),
                Vec2(268f, 564f)
            )
        ),
        LevelData(
            id = 15,
            title = "وابل الصخور",
            subtitle = "نحل من الجانبين وصخور من السماء!",
            heroStart = Vec2(210f, 546f),
            platforms = listOf(
                Platform(75f, 570f, 270f, 42f, PlatformStyle.STONE)
            ),
            beehives = listOf(
                BeehiveSpec(x = 70f, y = 215f, beeCount = 10),
                BeehiveSpec(x = 350f, y = 215f, beeCount = 10)
            ),
            boulders = listOf(
                BoulderSpec(x = 200f, y = 115f, radius = 23f, initialVx = -20f, delaySeconds = 0.3f),
                BoulderSpec(x = 235f, y = 175f, radius = 19f, initialVx = 24f, delaySeconds = 0.7f)
            ),
            maxInkLength = 960f,
            skyTheme = SkyTheme.ROCKY_CANYON,
            hintPath = listOf(
                Vec2(154f, 564f),
                Vec2(162f, 500f),
                Vec2(210f, 475f),
                Vec2(258f, 500f),
                Vec2(266f, 564f)
            )
        ),
        LevelData(
            id = 16,
            title = "الحافة الضيقة",
            subtitle = "الحافة ضيقة وفوق الحمم! اجعل الدرع يعانق الصخرة",
            heroStart = Vec2(210f, 506f),
            platforms = listOf(
                Platform(145f, 530f, 130f, 30f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 330f, y = 190f, beeCount = 20)
            ),
            boulders = listOf(
                BoulderSpec(x = 185f, y = 130f, radius = 20f, initialVx = -22f, delaySeconds = 0.35f)
            ),
            maxInkLength = 950f,
            skyTheme = SkyTheme.ROCKY_CANYON,
            hintPath = listOf(
                Vec2(156f, 524f),
                Vec2(162f, 462f),
                Vec2(210f, 446f),
                Vec2(258f, 462f),
                Vec2(264f, 524f)
            )
        ),

        // 17-20: Master puzzles combining lava gaps, bouncy springs, and multi-hive swarms
        LevelData(
            id = 17,
            title = "القفزة المرتدة",
            subtitle = "النابض يرتد بقوة! تجنبه وثبّت درعك على المنصة",
            heroStart = Vec2(210f, 526f),
            platforms = listOf(
                Platform(125f, 550f, 170f, 34f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            springs = listOf(
                SpringPadSpec(x = 45f, y = 595f, width = 62f),
                SpringPadSpec(x = 313f, y = 595f, width = 62f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 75f, y = 175f, beeCount = 11),
                BeehiveSpec(x = 345f, y = 175f, beeCount = 11)
            ),
            maxInkLength = 920f,
            skyTheme = SkyTheme.MOLTEN_STORM,
            hintPath = listOf(
                Vec2(156f, 544f),
                Vec2(162f, 484f),
                Vec2(210f, 470f),
                Vec2(258f, 484f),
                Vec2(264f, 544f)
            )
        ),
        LevelData(
            id = 18,
            title = "فوضى النوابض",
            subtitle = "نوابض، نحل، وصخرة متدحرجة! ثبّت قوسك داخل الحواجز",
            heroStart = Vec2(210f, 546f),
            platforms = listOf(
                Platform(30f, 570f, 360f, 44f, PlatformStyle.STONE)
            ),
            springs = listOf(
                SpringPadSpec(x = 55f, y = 552f, width = 64f),
                SpringPadSpec(x = 301f, y = 552f, width = 64f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 75f, y = 190f, beeCount = 11),
                BeehiveSpec(x = 345f, y = 190f, beeCount = 11)
            ),
            boulders = listOf(
                BoulderSpec(x = 210f, y = 120f, radius = 22f, initialVx = 26f, delaySeconds = 0.25f)
            ),
            maxInkLength = 960f,
            skyTheme = SkyTheme.MOLTEN_STORM,
            hintPath = listOf(
                Vec2(154f, 564f),
                Vec2(162f, 502f),
                Vec2(210f, 480f),
                Vec2(258f, 502f),
                Vec2(266f, 564f)
            )
        ),
        LevelData(
            id = 19,
            title = "حمم وصخور",
            subtitle = "الجزيرة صغيرة بين البركان والصخور — خط دقيق يكفي!",
            heroStart = Vec2(210f, 506f),
            platforms = listOf(
                Platform(138f, 530f, 144f, 32f, PlatformStyle.STONE),
                Platform(20f, 585f, 80f, 40f, PlatformStyle.STONE),
                Platform(320f, 585f, 80f, 40f, PlatformStyle.STONE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            springs = listOf(
                SpringPadSpec(x = 28f, y = 567f, width = 62f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 85f, y = 175f, beeCount = 12),
                BeehiveSpec(x = 335f, y = 175f, beeCount = 12)
            ),
            boulders = listOf(
                BoulderSpec(x = 185f, y = 115f, radius = 20f, initialVx = -20f, delaySeconds = 0.2f),
                BoulderSpec(x = 240f, y = 115f, radius = 20f, initialVx = 20f, delaySeconds = 0.55f)
            ),
            maxInkLength = 980f,
            skyTheme = SkyTheme.MOLTEN_STORM,
            hintPath = listOf(
                Vec2(154f, 524f),
                Vec2(160f, 464f),
                Vec2(210f, 444f),
                Vec2(260f, 464f),
                Vec2(266f, 524f)
            )
        ),
        LevelData(
            id = 20,
            title = "التحدي النهائي",
            subtitle = "٢٥ نحلة غاضبة وصخور وحمم ونوابض — أثبت براعتك!",
            heroStart = Vec2(210f, 516f),
            platforms = listOf(
                Platform(135f, 540f, 150f, 34f, PlatformStyle.STONE),
                Platform(20f, 560f, 76f, 36f, PlatformStyle.WOOD_BRIDGE),
                Platform(324f, 560f, 76f, 36f, PlatformStyle.WOOD_BRIDGE)
            ),
            lavaPits = listOf(
                LavaPit(0f, 665f, WORLD_WIDTH, 95f)
            ),
            springs = listOf(
                SpringPadSpec(x = 27f, y = 542f, width = 62f),
                SpringPadSpec(x = 331f, y = 542f, width = 62f)
            ),
            beehives = listOf(
                BeehiveSpec(x = 80f, y = 165f, beeCount = 13),
                BeehiveSpec(x = 340f, y = 165f, beeCount = 12)
            ),
            boulders = listOf(
                BoulderSpec(x = 195f, y = 110f, radius = 22f, initialVx = -24f, delaySeconds = 0.25f),
                BoulderSpec(x = 230f, y = 155f, radius = 20f, initialVx = 28f, delaySeconds = 0.65f)
            ),
            maxInkLength = 1020f,
            skyTheme = SkyTheme.MOLTEN_STORM,
            hintPath = listOf(
                Vec2(125f, 562f),
                Vec2(125f, 528f),
                Vec2(155f, 470f),
                Vec2(210f, 448f),
                Vec2(265f, 470f),
                Vec2(295f, 528f),
                Vec2(295f, 562f)
            )
        )
    )

    fun getById(id: Int): LevelData = all.firstOrNull { it.id == id } ?: all.first()
}
