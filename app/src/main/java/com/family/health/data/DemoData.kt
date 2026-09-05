// 契约：prototype/data.js 1:1 移植；实体结构对应 docs/02-数据库与同步.md
package com.family.health.data

import com.family.health.data.model.CheckupEvent
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Device
import com.family.health.data.model.IndicatorItem
import com.family.health.data.model.MedChange
import com.family.health.data.model.Measurement
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.Note
import com.family.health.data.model.Profile
import com.family.health.data.model.ReminderSetting
import com.family.health.data.model.Report
import com.family.health.data.model.WatchItem

object DemoData {

    const val FAMILY_NAME = "老张家"

    val devices = listOf(
        Device("d1", "爸爸", self = true),
        Device("d2", "妈妈"),
        Device("d3", "姑姑"),
    )

    val reminders: Map<String, ReminderSetting> = mapOf(
        "yeye" to ReminderSetting(medTimes = listOf("08:00", "20:00"), measureTimes = listOf("19:00"), advanceDays = listOf(7, 1, 0)),
        "nainai" to ReminderSetting(medTimes = listOf("08:00"), advanceDays = listOf(1, 0)),
        "baba" to ReminderSetting(advanceDays = listOf(0)),
    )

    private val yeye = Profile(
        id = "yeye", name = "爷爷", relation = "爷爷", gender = "male", birthDate = "1952-03-12",
        profileNote = "高血压、2型糖尿病、慢性肾病（CKD）。青霉素过敏。2023 年冠脉支架一枚。",
        meds = listOf(
            MedicationItem("m1", "氯沙坦钾片", "每次 50mg", listOf("morning"), "western", "long_term", "2026-08-15", supersedesId = "m4"),
            MedicationItem("m2", "二甲双胍", "每次 0.5g", listOf("morning", "evening"), "western", "long_term", "2025-11-20"),
            MedicationItem("m3", "阿托伐他汀钙片", "每次 20mg", listOf("bedtime"), "western", "long_term", "2026-08-15"),
            MedicationItem("m4", "缬沙坦", "每次 80mg", listOf("morning"), "western", "long_term", "2025-11-20", endDate = "2026-08-15"),
            MedicationItem("m5", "连花清瘟胶囊", "每次 4 粒", listOf("morning", "noon", "evening"), "tcm", "temporary", "2026-08-01", endDate = "2026-08-06"),
            MedicationItem("m6", "益肾健脾方", "7 剂，水煎服", listOf("morning", "evening"), "tcm", "temporary", "2026-06-02", endDate = "2026-06-16"),
        ),
        changes = listOf(
            MedChange("c1", "2026-08-15", "复查后调整用药", "e1"),
            MedChange("c2", "2026-08-01", "感冒，临时加药"),
            MedChange("c3", "2026-06-02", "复查后中药调理", "e2"),
            MedChange("c4", "2025-11-20", "初始建档"),
        ),
        daily = listOf(
            DailyMedItem("dl1", "氯沙坦钾片", doseText = "1 片", doseSlots = listOf("morning"), stockQty = 18.0, stockUnit = "片", dailyQty = 1.0),
            DailyMedItem("dl2", "二甲双胍", doseText = "1 片", doseSlots = listOf("morning", "evening"), stockQty = 42.0, stockUnit = "片", dailyQty = 2.0),
            DailyMedItem("dl3", "阿托伐他汀钙片", doseText = "1 片", doseSlots = listOf("bedtime"), stockQty = 9.0, stockUnit = "片", dailyQty = 1.0),
            DailyMedItem("dl4", "益肾健脾方", isTcm = true, tcmPacks = 5.0, tcmDaysPerPack = 2, tcmUsedDays = 1),
        ),
        events = listOf(
            CheckupEvent(
                "e1", "2026-08-15", "市人民医院", "肾内科",
                note = "尿蛋白控制一般，医生嘱低盐饮食，三个月后复查。",
                nextCheckupDate = "2026-11-15", medChangeSummary = "停缬沙坦 → 氯沙坦钾片；新增阿托伐他汀",
                reports = listOf(
                    Report("尿常规", "2026-08-15", attachments = 2, indicators = listOf(
                        IndicatorItem("尿蛋白", valueText = "1+", referenceRange = "阴性"),
                        IndicatorItem("尿白细胞", valueText = "阴性", referenceRange = "阴性"),
                        IndicatorItem("尿微量白蛋白", valueNumeric = 156.3, unit = "mg/L", referenceRange = "<30"),
                        IndicatorItem("尿肌酐", valueNumeric = 8.82, unit = "mmol/L"),
                        IndicatorItem("尿微量白蛋白/肌酐比值(ACR)", valueNumeric = 201.5, unit = "mg/g", referenceRange = "<30"),
                    )),
                    Report("肾功能+血糖", "2026-08-15", attachments = 1, indicators = listOf(
                        IndicatorItem("血肌酐", valueNumeric = 132.0, unit = "μmol/L", referenceRange = "41~81"),
                        IndicatorItem("尿素氮", valueNumeric = 9.1, unit = "mmol/L", referenceRange = "2.9~8.2"),
                        IndicatorItem("估算肾小球滤过率", valueNumeric = 58.0, unit = "ml/min"),
                        IndicatorItem("糖化血红蛋白", valueNumeric = 7.2, unit = "%", referenceRange = "4.0~6.0"),
                        IndicatorItem("空腹血糖", valueNumeric = 7.8, unit = "mmol/L", referenceRange = "3.9~6.1"),
                        IndicatorItem("血钙", valueNumeric = 2.31, unit = "mmol/L", referenceRange = "2.11~2.52"),
                    )),
                    Report("肾脏B超", "2026-08-15", attachments = 3,
                        conclusionText = "双肾大小形态正常，实质回声增强。右肾囊肿约 8mm，建议定期复查。"),
                ),
            ),
            CheckupEvent(
                "e2", "2026-06-02", "市人民医院", "内分泌科",
                note = "血糖控制尚可，继续当前用药。遵医嘱配合中药调理。",
                nextCheckupDate = "2026-08-15", medChangeSummary = "中药调理：益肾健脾方 7 剂",
                reports = listOf(
                    Report("糖尿病相关", "2026-06-02", attachments = 1, indicators = listOf(
                        IndicatorItem("糖化血红蛋白", valueNumeric = 7.5, unit = "%", referenceRange = "4.0~6.0"),
                        IndicatorItem("空腹血糖", valueNumeric = 7.0, unit = "mmol/L", referenceRange = "3.9~6.1"),
                    )),
                    Report("尿微量白蛋白", "2026-06-02", attachments = 1, indicators = listOf(
                        IndicatorItem("尿微量白蛋白", valueNumeric = 128.0, unit = "mg/L", referenceRange = "<30"),
                        IndicatorItem("ACR(尿)", valueNumeric = 180.0, unit = "mg/g", referenceRange = "<30"),
                    )),
                ),
            ),
            CheckupEvent(
                "e3", "2026-03-05", "市中医院", "肾病科",
                note = "首次系统复查，建立基线。", nextCheckupDate = "2026-06-02",
                reports = listOf(
                    Report("肾功能", "2026-03-05", attachments = 1, indicators = listOf(
                        IndicatorItem("血肌酐", valueNumeric = 120.0, unit = "μmol/L", referenceRange = "41~81"),
                        IndicatorItem("尿素氮", valueNumeric = 8.4, unit = "mmol/L", referenceRange = "2.9~8.2"),
                    )),
                    Report("糖化血红蛋白", "2026-03-05", indicators = listOf(
                        IndicatorItem("糖化血红蛋白", valueNumeric = 7.8, unit = "%", referenceRange = "4.0~6.0"),
                    )),
                ),
            ),
        ),
        measurements = listOf(
            Measurement("ms1", "bp", "2026-09-05 07:32", systolic = 136, diastolic = 85, heartRateBpm = 71, createdBy = "爸爸"),
            Measurement("ms2", "glucose", "2026-09-04 07:10", glucoseMmol = 6.8, glucoseContext = "fasting", createdBy = "妈妈"),
            Measurement("ms3", "bp", "2026-09-04 19:20", systolic = 143, diastolic = 89, heartRateBpm = 75, createdBy = "爸爸"),
            Measurement("ms4", "glucose", "2026-09-04 09:40", glucoseMmol = 8.9, glucoseContext = "after_meal_2h", createdBy = "妈妈"),
            Measurement("ms5", "bp", "2026-09-03 07:15", systolic = 139, diastolic = 87, heartRateBpm = 72, createdBy = "爸爸"),
            Measurement("ms6", "bp", "2026-09-03 12:40", systolic = 144, diastolic = 90, heartRateBpm = 76, createdBy = "妈妈"),
            Measurement("ms7", "bp", "2026-09-03 19:05", systolic = 141, diastolic = 88, heartRateBpm = 74, createdBy = "爸爸"),
            Measurement("ms8", "bp", "2026-09-03 21:30", systolic = 140, diastolic = 87, heartRateBpm = 73, createdBy = "爸爸"),
            Measurement("ms9", "bp", "2026-09-01 07:28", systolic = 138, diastolic = 86, heartRateBpm = 72, createdBy = "爸爸"),
            Measurement("ms10", "bp", "2026-09-01 19:02", systolic = 140, diastolic = 88, heartRateBpm = 73, createdBy = "妈妈"),
            Measurement("ms11", "glucose", "2026-09-01 21:30", glucoseMmol = 7.6, glucoseContext = "bedtime", createdBy = "爸爸"),
            Measurement("ms12", "glucose", "2026-08-30 07:15", glucoseMmol = 7.1, glucoseContext = "fasting", createdBy = "妈妈"),
            Measurement("ms13", "bp", "2026-08-28 19:12", systolic = 145, diastolic = 90, heartRateBpm = 76, createdBy = "爸爸"),
            Measurement("ms14", "bp", "2026-08-25 07:40", systolic = 139, diastolic = 87, heartRateBpm = 70, createdBy = "姑姑"),
            Measurement("ms15", "glucose", "2026-08-22 07:05", glucoseMmol = 6.5, glucoseContext = "fasting", createdBy = "妈妈"),
            Measurement("ms16", "bp", "2026-08-20 19:00", systolic = 142, diastolic = 89, heartRateBpm = 73, createdBy = "爸爸"),
            Measurement("ms17", "bp", "2026-08-18 07:33", systolic = 137, diastolic = 84, heartRateBpm = 71, createdBy = "爸爸"),
            Measurement("ms18", "bp", "2026-08-16 21:00", systolic = 135, diastolic = 84, heartRateBpm = 68, createdBy = "爸爸"),
            Measurement("ms19", "bp", "2026-07-29 07:30", systolic = 148, diastolic = 92, heartRateBpm = 78, createdBy = "爸爸"),
            Measurement("ms20", "bp", "2026-07-20 19:08", systolic = 144, diastolic = 91, heartRateBpm = 75, createdBy = "爸爸"),
            Measurement("ms21", "glucose", "2026-07-15 07:12", glucoseMmol = 7.4, glucoseContext = "fasting", createdBy = "妈妈"),
        ),
        notes = listOf(
            Note("n1", "爷爷这周有点感冒，注意观察血压变化。", createdBy = "爸爸", createdAtLabel = "09-03"),
            Note("n2", "下周三上午去取药，顺便问医生二甲双胍要不要调整。", remindAt = "09-11 18:00", remindTargetName = "妈妈", createdBy = "爸爸", createdAtLabel = "09-04"),
            Note("n3", "复查报告已整理录入，原件照片 6 张已归档。", done = true, createdBy = "爸爸", createdAtLabel = "08-16"),
        ),
        watchlist = listOf(
            WatchItem("ACR", listOf("尿微量白蛋白/肌酐比值(ACR)", "ACR(尿)"), "mg/g"),
            WatchItem("血肌酐", listOf("血肌酐"), "μmol/L"),
            WatchItem("糖化血红蛋白", listOf("糖化血红蛋白"), "%"),
        ),
    )

    private val nainai = Profile(
        id = "nainai", name = "奶奶", relation = "奶奶", gender = "female", birthDate = "1955-07-01",
        profileNote = "高血压。无药物过敏史。",
        meds = listOf(
            MedicationItem("m7", "苯磺酸氨氯地平片", "每次 5mg", listOf("morning"), "western", "long_term", "2025-06-10"),
        ),
        changes = listOf(MedChange("c5", "2025-06-10", "初始建档")),
        daily = listOf(
            DailyMedItem("dl5", "苯磺酸氨氯地平片", doseText = "1 片", doseSlots = listOf("morning"), stockQty = 25.0, stockUnit = "片", dailyQty = 1.0),
        ),
        events = listOf(
            CheckupEvent(
                "e4", "2026-05-18", "社区医院", "全科",
                note = "血压控制平稳，半年后随访。", nextCheckupDate = "2026-11-18",
                reports = listOf(
                    Report("血常规", "2026-05-18", attachments = 1, indicators = listOf(
                        IndicatorItem("血红蛋白", valueNumeric = 128.0, unit = "g/L", referenceRange = "115~150"),
                        IndicatorItem("白细胞", valueNumeric = 6.2, unit = "10⁹/L", referenceRange = "3.5~9.5"),
                    )),
                ),
            ),
        ),
        measurements = listOf(
            Measurement("ms22", "bp", "2026-09-04 08:10", systolic = 128, diastolic = 78, heartRateBpm = 69, createdBy = "妈妈"),
            Measurement("ms23", "bp", "2026-09-01 08:05", systolic = 131, diastolic = 80, heartRateBpm = 70, createdBy = "妈妈"),
            Measurement("ms24", "bp", "2026-08-25 08:12", systolic = 126, diastolic = 77, heartRateBpm = 68, createdBy = "姑姑"),
        ),
        notes = listOf(
            Note("n4", "奶奶降压药快吃完了，下周记得陪她去社区医院开药。", remindAt = "09-08 09:00", createdBy = "妈妈", createdAtLabel = "09-02"),
        ),
    )

    private val baba = Profile(
        id = "baba", name = "爸爸", relation = "本人", gender = "male", birthDate = "1980-11-23",
        measurements = listOf(
            Measurement("ms25", "bp", "2026-09-02 22:10", systolic = 129, diastolic = 82, heartRateBpm = 72, createdBy = "爸爸"),
            Measurement("ms26", "bp", "2026-08-19 22:05", systolic = 133, diastolic = 84, heartRateBpm = 74, createdBy = "爸爸"),
        ),
    )

    val members = listOf(yeye, nainai, baba)

    /** 导入示例文本（契约：docs/03-导入格式-v1.md §7，与 prototype/data.js 同款） */
    const val IMPORT_SAMPLE = """{
  "format": "family-health-import",
  "version": 1,
  "import_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "profile_ref": { "name": "爷爷" },
  "payload": {
    "type": "checkup_event",
    "event": {
      "checkup_date": "2026-09-04",
      "hospital": "市人民医院",
      "department": "肾内科",
      "note": "ACR 较上次略升，继续观察，三个月后复查。",
      "next_checkup_date": "2026-12-04",
      "reports": [
        { "title": "尿常规", "indicators": [
          { "item_name": "尿蛋白", "value": "1+", "reference_range": "阴性" },
          { "item_name": "尿微量白蛋白/肌酐比值(ACR)", "value": 215.8, "unit": "mg/g", "reference_range": "<30" }
        ] },
        { "title": "肾功能", "indicators": [
          { "item_name": "血肌酐", "value": 136, "unit": "μmol/L", "reference_range": "41~81" }
        ] }
      ]
    }
  }
}"""
}
