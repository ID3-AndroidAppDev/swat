package com.murimgod.kuas_cafeteria_app.ui.dayview

import com.murimgod.kuas_cafeteria_app.data.model.AllergenInfo

object HardcodedAllergens {
    // Japan's 8 mandatory (特定原材料) + common extended seen in KUAS menus
    val list: List<AllergenInfo> = listOf(
        AllergenInfo("egg",        "Egg",        "卵"),
        AllergenInfo("milk",       "Milk",       "乳"),
        AllergenInfo("wheat",      "Wheat",      "小麦"),
        AllergenInfo("shrimp",     "Shrimp",     "えび"),
        AllergenInfo("crab",       "Crab",       "かに"),
        AllergenInfo("buckwheat",  "Buckwheat",  "そば"),
        AllergenInfo("peanut",     "Peanut",     "落花生"),
        AllergenInfo("walnut",     "Walnut",     "くるみ"),
        AllergenInfo("soy",        "Soy",        "大豆"),
        AllergenInfo("mackerel",   "Mackerel",   "さば"),
        AllergenInfo("salmon",     "Salmon",     "さけ"),
        AllergenInfo("squid",      "Squid",      "いか"),
        AllergenInfo("chicken",    "Chicken",    "鶏肉"),
        AllergenInfo("pork",       "Pork",       "豚肉"),
        AllergenInfo("beef",       "Beef",       "牛肉"),
        AllergenInfo("sesame",     "Sesame",     "ごま"),
        AllergenInfo("orange",     "Orange",     "オレンジ"),
        AllergenInfo("apple",      "Apple",      "りんご"),
        AllergenInfo("banana",     "Banana",     "バナナ"),
        AllergenInfo("peach",      "Peach",      "もも"),
        AllergenInfo("cashew",     "Cashew",     "カシューナッツ"),
        AllergenInfo("almond",     "Almond",     "アーモンド"),
        AllergenInfo("gelatin",    "Gelatin",    "ゼラチン"),
    )
}
