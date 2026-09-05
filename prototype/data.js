// 演示数据（对应 02-数据库与同步.md 的实体结构）
const DB = {
  familyName: '老张家',
  devices: [
    { id: 'd1', name: '爸爸', self: true },
    { id: 'd2', name: '妈妈' },
    { id: 'd3', name: '姑姑' },
  ],
  members: [
    {
      id: 'yeye', name: '爷爷', relation: '爷爷', gender: '男', birth: '1952-03-12',
      profileNote: '高血压、2型糖尿病、慢性肾病（CKD）。青霉素过敏。2023 年冠脉支架一枚。',
      meds: [
        { id: 'm1', name: '氯沙坦钾片', dosage: '每次 50mg', slots: ['morning'], kind: 'western', cat: 'long', start: '2026-08-15', end: null, supersedes: 'm4' },
        { id: 'm2', name: '二甲双胍', dosage: '每次 0.5g', slots: ['morning', 'evening'], kind: 'western', cat: 'long', start: '2025-11-20', end: null },
        { id: 'm3', name: '阿托伐他汀钙片', dosage: '每次 20mg', slots: ['bedtime'], kind: 'western', cat: 'long', start: '2026-08-15', end: null },
        { id: 'm4', name: '缬沙坦', dosage: '每次 80mg', slots: ['morning'], kind: 'western', cat: 'long', start: '2025-11-20', end: '2026-08-15' },
        { id: 'm5', name: '连花清瘟胶囊', dosage: '每次 4 粒', slots: ['morning', 'noon', 'evening'], kind: 'tcm', cat: 'temp', start: '2026-08-01', end: '2026-08-06' },
        { id: 'm6', name: '益肾健脾方', dosage: '7 剂，水煎服', slots: ['morning', 'evening'], kind: 'tcm', cat: 'temp', start: '2026-06-02', end: '2026-06-16' },
      ],
      changes: [
        { id: 'c1', date: '2026-08-15', note: '复查后调整用药', eventId: 'e1' },
        { id: 'c2', date: '2026-08-01', note: '感冒，临时加药', eventId: null },
        { id: 'c3', date: '2026-06-02', note: '复查后中药调理', eventId: 'e2' },
        { id: 'c4', date: '2025-11-20', note: '初始建档', eventId: null },
      ],
      daily: [
        { id: 'dl1', name: '氯沙坦钾片', dose: '1 片', slots: ['morning'], stock: 18, unit: '片', daily: 1 },
        { id: 'dl2', name: '二甲双胍', dose: '1 片', slots: ['morning', 'evening'], stock: 42, unit: '片', daily: 2 },
        { id: 'dl3', name: '阿托伐他汀钙片', dose: '1 片', slots: ['bedtime'], stock: 9, unit: '片', daily: 1 },
        { id: 'dl4', name: '益肾健脾方', tcm: true, packs: 5, daysPerPack: 2, usedDays: 1 },
      ],
      events: [
        {
          id: 'e1', date: '2026-08-15', hospital: '市人民医院', dept: '肾内科',
          note: '尿蛋白控制一般，医生嘱低盐饮食，三个月后复查。',
          next: '2026-11-15', medChange: '停缬沙坦 → 氯沙坦钾片；新增阿托伐他汀',
          reports: [
            { title: '尿常规', date: '2026-08-15', attachments: 2, conclusion: '', indicators: [
              { n: '尿蛋白', v: '1+', r: '阴性' },
              { n: '尿白细胞', v: '阴性', r: '阴性' },
              { n: '尿微量白蛋白', v: 156.3, u: 'mg/L', r: '<30' },
              { n: '尿肌酐', v: 8.82, u: 'mmol/L', r: '' },
              { n: '尿微量白蛋白/肌酐比值(ACR)', v: 201.5, u: 'mg/g', r: '<30' },
            ]},
            { title: '肾功能+血糖', date: '2026-08-15', attachments: 1, conclusion: '', indicators: [
              { n: '血肌酐', v: 132, u: 'μmol/L', r: '41~81' },
              { n: '尿素氮', v: 9.1, u: 'mmol/L', r: '2.9~8.2' },
              { n: '估算肾小球滤过率', v: 58, u: 'ml/min', r: '' },
              { n: '糖化血红蛋白', v: 7.2, u: '%', r: '4.0~6.0' },
              { n: '空腹血糖', v: 7.8, u: 'mmol/L', r: '3.9~6.1' },
              { n: '血钙', v: 2.31, u: 'mmol/L', r: '2.11~2.52' },
            ]},
            { title: '肾脏B超', date: '2026-08-15', attachments: 3, conclusion: '双肾大小形态正常，实质回声增强。右肾囊肿约 8mm，建议定期复查。', indicators: [] },
          ],
        },
        {
          id: 'e2', date: '2026-06-02', hospital: '市人民医院', dept: '内分泌科',
          note: '血糖控制尚可，继续当前用药。遵医嘱配合中药调理。',
          next: '2026-08-15', medChange: '中药调理：益肾健脾方 7 剂',
          reports: [
            { title: '糖尿病相关', date: '2026-06-02', attachments: 1, conclusion: '', indicators: [
              { n: '糖化血红蛋白', v: 7.5, u: '%', r: '4.0~6.0' },
              { n: '空腹血糖', v: 7.0, u: 'mmol/L', r: '3.9~6.1' },
            ]},
            { title: '尿微量白蛋白', date: '2026-06-02', attachments: 1, conclusion: '', indicators: [
              { n: '尿微量白蛋白', v: 128, u: 'mg/L', r: '<30' },
              { n: 'ACR(尿)', v: 180, u: 'mg/g', r: '<30' },
            ]},
          ],
        },
        {
          id: 'e3', date: '2026-03-05', hospital: '市中医院', dept: '肾病科',
          note: '首次系统复查，建立基线。', next: '2026-06-02', medChange: '',
          reports: [
            { title: '肾功能', date: '2026-03-05', attachments: 1, conclusion: '', indicators: [
              { n: '血肌酐', v: 120, u: 'μmol/L', r: '41~81' },
              { n: '尿素氮', v: 8.4, u: 'mmol/L', r: '2.9~8.2' },
            ]},
            { title: '糖化血红蛋白', date: '2026-03-05', attachments: 0, conclusion: '', indicators: [
              { n: '糖化血红蛋白', v: 7.8, u: '%', r: '4.0~6.0' },
            ]},
          ],
        },
      ],
      measurements: [
        { type: 'bp', at: '2026-09-05 07:32', sys: 136, dia: 85, hr: 71, by: '爸爸' },
        { type: 'glucose', at: '2026-09-04 07:10', glu: 6.8, ctx: 'fasting', by: '妈妈' },
        { type: 'bp', at: '2026-09-04 19:20', sys: 143, dia: 89, hr: 75, by: '爸爸' },
        { type: 'glucose', at: '2026-09-04 09:40', glu: 8.9, ctx: 'after_meal_2h', by: '妈妈' },
        { type: 'bp', at: '2026-09-03 07:15', sys: 139, dia: 87, hr: 72, by: '爸爸' },
        { type: 'bp', at: '2026-09-03 12:40', sys: 144, dia: 90, hr: 76, by: '妈妈' },
        { type: 'bp', at: '2026-09-03 19:05', sys: 141, dia: 88, hr: 74, by: '爸爸' },
        { type: 'bp', at: '2026-09-03 21:30', sys: 140, dia: 87, hr: 73, by: '爸爸' },
        { type: 'bp', at: '2026-09-01 07:28', sys: 138, dia: 86, hr: 72, by: '爸爸' },
        { type: 'bp', at: '2026-09-01 19:02', sys: 140, dia: 88, hr: 73, by: '妈妈' },
        { type: 'glucose', at: '2026-09-01 21:30', glu: 7.6, ctx: 'bedtime', by: '爸爸' },
        { type: 'glucose', at: '2026-08-30 07:15', glu: 7.1, ctx: 'fasting', by: '妈妈' },
        { type: 'bp', at: '2026-08-28 19:12', sys: 145, dia: 90, hr: 76, by: '爸爸' },
        { type: 'bp', at: '2026-08-25 07:40', sys: 139, dia: 87, hr: 70, by: '姑姑' },
        { type: 'glucose', at: '2026-08-22 07:05', glu: 6.5, ctx: 'fasting', by: '妈妈' },
        { type: 'bp', at: '2026-08-20 19:00', sys: 142, dia: 89, hr: 73, by: '爸爸' },
        { type: 'bp', at: '2026-08-18 07:33', sys: 137, dia: 84, hr: 71, by: '爸爸' },
        { type: 'bp', at: '2026-08-16 21:00', sys: 135, dia: 84, hr: 68, by: '爸爸' },
        { type: 'bp', at: '2026-07-29 07:30', sys: 148, dia: 92, hr: 78, by: '爸爸' },
        { type: 'bp', at: '2026-07-20 19:08', sys: 144, dia: 91, hr: 75, by: '爸爸' },
        { type: 'glucose', at: '2026-07-15 07:12', glu: 7.4, ctx: 'fasting', by: '妈妈' },
      ],
      notes: [
        { id: 'n1', text: '爷爷这周有点感冒，注意观察血压变化。', done: false, remind: null, by: '爸爸', at: '09-03' },
        { id: 'n2', text: '下周三上午去取药，顺便问医生二甲双胍要不要调整。', done: false, remind: { at: '09-11 18:00', target: '妈妈' }, by: '爸爸', at: '09-04' },
        { id: 'n3', text: '复查报告已整理录入，原件照片 6 张已归档。', done: true, remind: null, by: '爸爸', at: '08-16' },
      ],
      watchlist: [
        { name: 'ACR', aliases: ['尿微量白蛋白/肌酐比值(ACR)', 'ACR(尿)'], unit: 'mg/g' },
        { name: '血肌酐', aliases: ['血肌酐'], unit: 'μmol/L' },
        { name: '糖化血红蛋白', aliases: ['糖化血红蛋白'], unit: '%' },
      ],
    },
    {
      id: 'nainai', name: '奶奶', relation: '奶奶', gender: '女', birth: '1955-07-01',
      profileNote: '高血压。无药物过敏史。',
      meds: [
        { id: 'm7', name: '苯磺酸氨氯地平片', dosage: '每次 5mg', slots: ['morning'], kind: 'western', cat: 'long', start: '2025-06-10', end: null },
      ],
      changes: [
        { id: 'c5', date: '2025-06-10', note: '初始建档', eventId: null },
      ],
      daily: [
        { id: 'dl5', name: '苯磺酸氨氯地平片', dose: '1 片', slots: ['morning'], stock: 25, unit: '片', daily: 1 },
      ],
      events: [
        { id: 'e4', date: '2026-05-18', hospital: '社区医院', dept: '全科', note: '血压控制平稳，半年后随访。', next: '2026-11-18', medChange: '',
          reports: [
            { title: '血常规', date: '2026-05-18', attachments: 1, conclusion: '', indicators: [
              { n: '血红蛋白', v: 128, u: 'g/L', r: '115~150' },
              { n: '白细胞', v: 6.2, u: '10⁹/L', r: '3.5~9.5' },
            ]},
          ]},
      ],
      measurements: [
        { type: 'bp', at: '2026-09-04 08:10', sys: 128, dia: 78, hr: 69, by: '妈妈' },
        { type: 'bp', at: '2026-09-01 08:05', sys: 131, dia: 80, hr: 70, by: '妈妈' },
        { type: 'bp', at: '2026-08-25 08:12', sys: 126, dia: 77, hr: 68, by: '姑姑' },
      ],
      notes: [
        { id: 'n4', text: '奶奶降压药快吃完了，下周记得陪她去社区医院开药。', done: false, remind: { at: '09-08 09:00', target: null }, by: '妈妈', at: '09-02' },
      ],
      watchlist: [],
    },
    {
      id: 'baba', name: '爸爸', relation: '本人', gender: '男', birth: '1980-11-23',
      profileNote: '',
      meds: [], events: [], changes: [], daily: [],
      measurements: [
        { type: 'bp', at: '2026-09-02 22:10', sys: 129, dia: 82, hr: 72, by: '爸爸' },
        { type: 'bp', at: '2026-08-19 22:05', sys: 133, dia: 84, hr: 74, by: '爸爸' },
      ],
      notes: [], watchlist: [],
    },
  ],
};

// 导入示例（对应 03-导入格式-v1.md 第 7 节）
const IMPORT_SAMPLE = JSON.stringify({
  format: 'family-health-import', version: 1,
  import_id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
  profile_ref: { name: '爷爷' },
  payload: { type: 'checkup_event', event: {
    checkup_date: '2026-09-04', hospital: '市人民医院', department: '肾内科',
    note: 'ACR 较上次略升，继续观察，三个月后复查。', next_checkup_date: '2026-12-04',
    reports: [
      { title: '尿常规', indicators: [
        { item_name: '尿蛋白', value: '1+', reference_range: '阴性' },
        { item_name: '尿微量白蛋白/肌酐比值(ACR)', value: 215.8, unit: 'mg/g', reference_range: '<30' },
      ]},
      { title: '肾功能', indicators: [
        { item_name: '血肌酐', value: 136, unit: 'μmol/L', reference_range: '41~81' },
      ]},
    ],
  }},
}, null, 2);
