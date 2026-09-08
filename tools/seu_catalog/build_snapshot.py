#!/usr/bin/env python3
"""Build the reviewed SEU public curriculum snapshot used by vCampus.

Only Python's standard library is required. Department names/homepages are
collected from the official SEU department page. Curriculum rows below are a
reviewed transcription of the official PDF tables because those PDFs use
custom Chinese font maps that do not extract reliably as text.
"""

from __future__ import print_function

import argparse
import csv
import io
import os
import sys
from html.parser import HTMLParser
from urllib.request import Request, urlopen


SNAPSHOT_DATE = "2026-08-31"
DEPARTMENT_SOURCE = "https://www.seu.edu.cn/17399/list.htm"

SOURCES = [
    ("SEU_DEPT", "院系列表", "东南大学院系设置", "2026", DEPARTMENT_SOURCE),
    ("CS_2023", "培养方案", "2023级计算机科学与技术本科专业培养方案", "2023",
     "https://cs.seu.edu.cn/_upload/article/files/c0/e1/a1ddff26422fb6f655d8d4f65a1c/ba192a48-19cf-4f08-b707-c39f12ecca36.pdf"),
    ("SE_2023", "培养方案", "2023级软件工程本科专业培养方案", "2023",
     "https://cs.seu.edu.cn/_upload/article/files/6d/10/9792a12f4db7b53a3d6301db8a8c/15d8b627-5777-46c2-af5b-8bb76a7c2c2b.pdf"),
    ("AI_2023", "培养方案", "2023级人工智能本科专业培养方案", "2023",
     "https://cs.seu.edu.cn/_upload/article/files/ce/96/460b19804b69a1df18e4ffeb8d7f/0e756f75-618f-4b41-9372-5743a1a09d36.pdf"),
    ("CYBER_2025", "培养方案", "2025级网络空间安全学院本科培养方案", "2025",
     "https://cyber.seu.edu.cn/_upload/article/files/03/00/16c6c414442686387b259209cfc7/3e6a752c-3e91-4bac-9589-89015f43bdbe.pdf"),
    ("RADIO_2025", "培养方案", "2025级信息工程、海洋信息工程本科培养方案", "2025",
     "https://radio.seu.edu.cn/_upload/article/files/62/23/db30cf5446009d6beb9cc5e41a3d/051e14ec-5b6c-4945-839f-69f21ee43422.pdf"),
    ("INS_2025", "培养方案", "2025级仪器科学与工程学院本科培养方案", "2025",
     "https://ins.seu.edu.cn/_upload/article/files/85/70/d182645e4f388c89654c939c8593/34130697-0248-4a98-ad86-4f72b7ae5b5b.pdf"),
]

DEPARTMENT_IDS = {
    "建筑学院": "ARCH", "机械工程学院": "ME", "能源与环境学院": "POWER",
    "信息科学与工程学院": "RADIO", "土木工程学院": "CIVIL",
    "电子科学与工程学院": "ELECTRONIC", "数学学院": "MATH",
    "自动化学院": "AUTO", "计算机科学与工程学院": "CS", "物理学院": "PHYS",
    "生物科学与医学工程学院": "BME", "材料科学与工程学院": "SMSE",
    "人文学院": "HUMAN", "经济管理学院": "ECON", "电气工程学院": "EE",
    "外国语学院": "SFL", "体育系": "PE", "化学化工学院": "CHEM",
    "交通学院": "TRANS", "仪器科学与工程学院": "INS", "艺术学院": "ARTS",
    "法学院": "LAW", "医学院": "MED", "公共卫生学院": "PH",
    "吴健雄学院": "WJX", "海外教育学院": "CIS", "软件学院": "SOFTWARE",
    "微电子学院": "IC", "马克思主义学院": "MARX", "网络空间安全学院": "CYBER",
    "人工智能学院": "AI", "东南大学雷恩研究生学院": "RENNES",
    "东南大学—蒙纳士大学苏州联合研究生院": "SMJGS",
    "生命科学与技术学院": "ILS",
}

FALLBACK_DEPARTMENTS = [
    ("建筑学院", "http://arch.seu.edu.cn/"), ("机械工程学院", "http://me.seu.edu.cn/"),
    ("能源与环境学院", "http://power.seu.edu.cn/main.htm"),
    ("信息科学与工程学院", "http://radio.seu.edu.cn/"),
    ("土木工程学院", "http://civil.seu.edu.cn/"),
    ("电子科学与工程学院", "http://electronic.seu.edu.cn"),
    ("数学学院", "http://math.seu.edu.cn/"), ("自动化学院", "http://automation.seu.edu.cn/"),
    ("计算机科学与工程学院", "http://cse.seu.edu.cn/"),
    ("物理学院", "http://physics.seu.edu.cn/"), ("生物科学与医学工程学院", "http://bme.seu.edu.cn"),
    ("材料科学与工程学院", "http://smse.seu.edu.cn/"), ("人文学院", "http://rwxy.seu.edu.cn/"),
    ("经济管理学院", "http://em.seu.edu.cn/"), ("电气工程学院", "http://ee.seu.edu.cn/"),
    ("外国语学院", "http://sfl.seu.edu.cn/"), ("体育系", "http://tyx.seu.edu.cn/"),
    ("化学化工学院", "http://chem.seu.edu.cn/"), ("交通学院", "http://tc.seu.edu.cn/"),
    ("仪器科学与工程学院", "http://ins.seu.edu.cn"), ("艺术学院", "http://arts.seu.edu.cn/"),
    ("法学院", "http://law.seu.edu.cn/"), ("医学院", "http://med.seu.edu.cn"),
    ("公共卫生学院", "http://gw.seu.edu.cn/"), ("吴健雄学院", "http://wjx.seu.edu.cn/"),
    ("海外教育学院", "http://cis.seu.edu.cn/"), ("软件学院", "http://cose.seu.edu.cn/"),
    ("微电子学院", "http://ic.seu.edu.cn/"), ("马克思主义学院", "http://marxism.seu.edu.cn/"),
    ("网络空间安全学院", "http://cyber.seu.edu.cn/"), ("人工智能学院", "http://ai.seu.edu.cn"),
    ("东南大学雷恩研究生学院", ""),
    ("东南大学—蒙纳士大学苏州联合研究生院", "http://smjgs.seu.edu.cn/"),
    ("生命科学与技术学院", "http://ils.seu.edu.cn/"),
]

MAJORS = [
    ("080901", "CS", "计算机科学与技术", "工学", 4, "CS_2023"),
    ("080902", "SOFTWARE", "软件工程", "工学", 4, "SE_2023"),
    ("080717T", "AI", "人工智能", "工学", 4, "AI_2023"),
    ("080911TK", "CYBER", "网络空间安全", "工学", 4, "CYBER_2025"),
    ("080904K", "CYBER", "信息安全", "工学", 4, "CYBER_2025"),
    ("080706", "RADIO", "信息工程", "工学", 4, "RADIO_2025"),
    ("080718T", "RADIO", "海洋信息工程", "工学", 4, "RADIO_2025"),
    ("080301", "INS", "测控技术与仪器", "工学", 4, "INS_2025"),
    ("080303T", "INS", "智能感知工程", "工学", 4, "INS_2025"),
]

# id, department, name, credits, lecture hours, practice hours, type, source,
# [(major id, recommended year, semester, required)]
COURSES = [
    ("B71S0032", "CS", "编译原理", 4, 56, 16, "专业主干", "CS_2023", [("080901",3,2,1),("080902",3,2,1)]),
    ("B09N0014", "CS", "计算机网络", 3, 40, 16, "专业主干", "CS_2023", [("080901",3,2,1),("080902",3,2,1)]),
    ("B09D0012", "CS", "数据库原理", 3, 40, 16, "专业主干", "CS_2023", [("080901",3,2,1),("080902",3,2,1)]),
    ("B09S0061", "CS", "软件工程", 3, 40, 16, "专业主干", "CS_2023", [("080901",3,3,1),("080902",3,3,1)]),
    ("B09G1011", "CS", "信号与系统", 3, 40, 16, "专业选修", "CS_2023", [("080901",2,3,0)]),
    ("B09D1011", "CS", "高级数据结构（全英文）", 2, 32, 0, "专业选修", "CS_2023", [("080901",2,3,0)]),
    ("B09N1021", "CS", "物联网导论（研讨）", 2, 24, 16, "专业选修", "CS_2023", [("080901",3,3,0)]),
    ("B09D1021", "CS", "大数据处理（研讨）", 2, 24, 16, "专业选修", "CS_2023", [("080901",3,2,0)]),
    ("B09G1031", "CS", "计算机图形学（研讨）", 2, 24, 16, "专业选修", "CS_2023", [("080901",3,2,0)]),
    ("B09N1031", "CS", "分布计算新技术（研讨）", 2, 16, 32, "专业选修", "CS_2023", [("080901",3,2,0)]),
    ("B09D1051", "CS", "信息检索（研讨）", 2, 24, 16, "专业选修", "CS_2023", [("080901",3,3,0)]),
    ("B09D1061", "CS", "数据仓库与数据挖掘（研讨）", 2, 24, 16, "专业选修", "CS_2023", [("080901",3,3,0)]),
    ("B09N1043", "CS", "网络与信息安全（研讨）", 2, 16, 32, "专业选修", "CS_2023", [("080901",3,3,0)]),
    ("B71S1021", "SOFTWARE", "Python编程（研讨）", 2, 24, 16, "专业选修", "SE_2023", [("080902",2,2,0)]),
    ("B71S1170", "SOFTWARE", "软件建模与UML", 2, 24, 16, "专业选修", "SE_2023", [("080902",2,2,0)]),
    ("B09S1031", "SOFTWARE", "Java程序设计", 2, 24, 16, "专业选修", "SE_2023", [("080902",3,3,0)]),
    ("B71S1041", "SOFTWARE", "Java设计模式（研讨）", 2, 24, 16, "专业选修", "SE_2023", [("080902",3,2,0)]),
    ("B58A0011", "AI", "人工智能导论", 3, 40, 16, "专业基础", "AI_2023", [("080717T",2,2,1)]),
    ("B09A1111", "AI", "机器学习（研讨）", 2, 24, 16, "专业选修", "AI_2023", [("080717T",3,2,0)]),
    ("B09A1131", "AI", "模式识别（全英文、研讨）", 2, 24, 16, "专业选修", "AI_2023", [("080717T",3,2,1)]),
    ("B58A1041", "AI", "深度学习与应用（研讨）", 2, 24, 16, "专业选修", "AI_2023", [("080717T",3,2,0)]),
    ("B58A1061", "AI", "强化学习", 2, 32, 0, "专业选修", "AI_2023", [("080717T",3,3,0)]),
    ("B09G1053", "AI", "机器视觉与应用", 2, 32, 0, "专业选修", "AI_2023", [("080717T",3,3,0)]),
    ("B58A1102", "AI", "知识图谱及应用（全英文、研讨）", 2, 24, 16, "专业选修", "AI_2023", [("080717T",3,1,0)]),
    ("B58A1171", "AI", "知识表示与推理（全英文、研讨）", 2, 24, 16, "专业选修", "AI_2023", [("080717T",3,2,0)]),
    ("B5710022", "CYBER", "计算机组成原理", 4, 48, 32, "学科基础", "CYBER_2025", [("080911TK",2,2,1),("080904K",2,2,1)]),
    ("B5710051", "CYBER", "数据结构基础", 4, 64, 0, "学科基础", "CYBER_2025", [("080911TK",2,2,1),("080904K",2,2,1)]),
    ("B5710071", "CYBER", "网络空间安全数学基础", 3, 48, 0, "学科基础", "CYBER_2025", [("080911TK",2,2,1),("080904K",2,2,1)]),
    ("B5710121", "CYBER", "操作系统", 4, 48, 32, "专业主干", "CYBER_2025", [("080911TK",2,3,1),("080904K",2,3,1)]),
    ("B5710141", "CYBER", "计算机网络", 3, 48, 0, "专业主干", "CYBER_2025", [("080911TK",2,3,1),("080904K",2,3,1)]),
    ("B5710161", "CYBER", "密码学", 3, 48, 0, "专业主干", "CYBER_2025", [("080911TK",2,3,1),("080904K",2,3,1)]),
    ("B5710132", "CYBER", "编译方法（双语）", 3, 32, 32, "专业主干", "CYBER_2025", [("080911TK",3,2,1)]),
    ("B5710151", "CYBER", "机器学习", 3, 32, 32, "专业主干", "CYBER_2025", [("080911TK",3,2,1)]),
    ("B5710180", "CYBER", "系统安全", 3, 48, 0, "专业主干", "CYBER_2025", [("080911TK",3,3,1),("080904K",3,3,1)]),
    ("B5710190", "CYBER", "网络空间安全的法律基础", 2, 32, 0, "专业方向", "CYBER_2025", [("080911TK",2,3,1)]),
    ("B5710220", "CYBER", "算法分析与设计", 2, 32, 0, "专业方向", "CYBER_2025", [("080911TK",3,2,1)]),
    ("B5710243", "CYBER", "数据库原理与技术（双语）", 3, 48, 0, "专业方向", "CYBER_2025", [("080911TK",3,2,1)]),
    ("B5710252", "CYBER", "WEB安全（全英文）", 2, 16, 32, "专业选修", "CYBER_2025", [("080911TK",4,2,0),("080904K",4,2,0)]),
    ("B0401082", "RADIO", "信息通信网（双语）", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,2,1),("080718T",3,2,1)]),
    ("B0402010", "RADIO", "计算机组织与结构（双语）I", 2, 32, 0, "专业主干", "RADIO_2025", [("080706",3,2,1),("080718T",3,2,1)]),
    ("B0412032", "RADIO", "通信原理", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,2,1),("080718T",3,2,1)]),
    ("B0422042", "RADIO", "数字信号处理", 3, 45, 6, "专业主干", "RADIO_2025", [("080706",3,2,1),("080718T",3,2,1)]),
    ("B0432060", "RADIO", "微波工程基础", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,2,1),("080718T",3,2,1)]),
    ("B0442050", "RADIO", "通信电子线路", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,2,1),("080718T",3,2,1)]),
    ("B0412070", "RADIO", "数字通信（双语）", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,3,1),("080718T",3,3,1)]),
    ("B0422080", "RADIO", "统计信号处理", 3, 40, 16, "专业主干", "RADIO_2025", [("080706",3,3,1),("080718T",3,3,1)]),
    ("B0432111", "RADIO", "微波器件原理与芯片设计", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,3,1)]),
    ("B0442090", "RADIO", "专用集成电路设计", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,3,1)]),
    ("B0452010", "RADIO", "信息安全", 3, 48, 0, "专业主干", "RADIO_2025", [("080706",3,3,1),("080718T",3,3,1)]),
    ("B0423252", "RADIO", "人工智能与深度学习", 3, 40, 16, "专业主干", "RADIO_2025", [("080706",3,3,1),("080718T",3,3,1)]),
    ("B2201020", "INS", "电路基础", 4, 64, 0, "学科基础", "INS_2025", [("080301",2,2,1),("080303T",2,2,1)]),
    ("B2201082", "INS", "信息通信网络概论", 2, 28, 8, "学科基础", "INS_2025", [("080301",2,3,1),("080303T",2,3,1)]),
    ("B2201041", "INS", "计算机结构与逻辑设计", 3, 48, 0, "学科基础", "INS_2025", [("080301",2,2,1),("080303T",2,2,1)]),
    ("B2201030", "INS", "信号与系统", 3, 48, 0, "学科基础", "INS_2025", [("080301",2,3,1),("080303T",2,3,1)]),
    ("B2201051", "INS", "电子电路基础", 3, 48, 0, "学科基础", "INS_2025", [("080301",2,3,1),("080303T",2,3,1)]),
    ("B2201111", "INS", "人工智能原理与实践", 3, 40, 16, "学科基础", "INS_2025", [("080301",2,3,1),("080303T",2,3,1)]),
    ("B2201060", "INS", "微机系统与接口（双语）", 3, 48, 0, "学科基础", "INS_2025", [("080301",2,3,1),("080303T",2,3,1)]),
    ("B2201071", "INS", "自动控制原理", 3, 40, 16, "学科基础", "INS_2025", [("080301",3,2,1),("080303T",3,2,1)]),
    ("B2202120", "INS", "智能传感器技术", 3, 44, 8, "专业主干", "INS_2025", [("080303T",3,2,1)]),
    ("B2202130", "INS", "图像处理与计算机视觉", 3, 44, 8, "专业主干", "INS_2025", [("080303T",3,3,1)]),
    ("B2202190", "INS", "智能感知系统设计与实践I", 3, 32, 32, "专业主干", "INS_2025", [("080303T",3,3,1)]),
    ("B2202200", "INS", "智能感知系统设计与实践II", 2, 16, 32, "专业主干", "INS_2025", [("080303T",4,2,0)]),
    ("B2202151", "INS", "机器学习", 3, 44, 8, "专业主干", "INS_2025", [("080303T",3,2,1)]),
    ("B2202101", "INS", "误差理论与数据处理（全英文）", 3, 48, 0, "专业主干", "INS_2025", [("080303T",3,2,1)]),
    ("B2203011", "INS", "数据库技术及应用", 2, 24, 16, "专业选修", "INS_2025", [("080301",3,2,0),("080303T",3,2,0)]),
    ("B2203021", "INS", "虚拟现实与数据可视化", 2, 24, 16, "专业选修", "INS_2025", [("080301",3,2,0),("080303T",3,2,0)]),
]


class DepartmentParser(HTMLParser):
    def __init__(self):
        HTMLParser.__init__(self)
        self.in_department_block = False
        self.depth = 0
        self.in_li = False
        self.text = []
        self.href = ""
        self.rows = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "div" and "jg" in attrs.get("class", "").split():
            self.in_department_block = True
            self.depth = 1
        elif self.in_department_block and tag == "div":
            self.depth += 1
        elif self.in_department_block and tag == "li":
            self.in_li = True
            self.text = []
            self.href = ""
        elif self.in_li and tag == "a":
            self.href = attrs.get("href", "")

    def handle_endtag(self, tag):
        if self.in_department_block and tag == "li" and self.in_li:
            name = "".join(self.text).strip()
            if name:
                self.rows.append((name, self.href))
            self.in_li = False
        elif self.in_department_block and tag == "div":
            self.depth -= 1
            if self.depth == 0:
                self.in_department_block = False

    def handle_data(self, data):
        if self.in_li:
            self.text.append(data)


def fetch(url):
    request = Request(url, headers={"User-Agent": "SEU-vCampus-course-project/1.0"})
    with urlopen(request, timeout=30) as response:
        return response.read()


def collect_departments(offline):
    if offline:
        return FALLBACK_DEPARTMENTS
    html = fetch(DEPARTMENT_SOURCE).decode("utf-8", "replace")
    parser = DepartmentParser()
    parser.feed(html)
    if set(name for name, _ in parser.rows) != set(DEPARTMENT_IDS):
        raise RuntimeError("Official department list changed; review before regenerating")
    return parser.rows


def verify_sources(offline):
    if offline:
        return
    for source in SOURCES[1:]:
        data = fetch(source[4])
        if not data.startswith(b"%PDF"):
            raise RuntimeError("Source is not a PDF: " + source[4])


def write_csv(path, header, rows):
    with io.open(path, "w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default=os.path.join(
        "vcampus-server", "src", "main", "resources", "seed", "seu"))
    parser.add_argument("--snapshot-date", default=SNAPSHOT_DATE)
    parser.add_argument("--offline", action="store_true",
                        help="regenerate from the last reviewed department fallback")
    args = parser.parse_args()

    departments = collect_departments(args.offline)
    verify_sources(args.offline)
    if not os.path.isdir(args.output_dir):
        os.makedirs(args.output_dir)

    source_year = dict((row[0], row[3]) for row in SOURCES)
    source_url = dict((row[0], row[4]) for row in SOURCES)
    write_csv(os.path.join(args.output_dir, "sources.csv"),
              ["sourceId", "sourceType", "title", "sourceYear", "sourceUrl", "collectedAt"],
              [row + (args.snapshot_date,) for row in SOURCES])
    write_csv(os.path.join(args.output_dir, "departments.csv"),
              ["departmentId", "departmentName", "homepageUrl", "sourceUrl", "collectedAt", "active"],
              [(DEPARTMENT_IDS[name], name, homepage, DEPARTMENT_SOURCE,
                args.snapshot_date, "true") for name, homepage in departments])
    write_csv(os.path.join(args.output_dir, "majors.csv"),
              ["majorId", "departmentId", "majorName", "degreeType", "durationYears",
               "sourceYear", "sourceId", "sourceUrl", "active"],
              [(major_id, department_id, name, degree, years, source_year[source_id],
                source_id, source_url[source_id], "true")
               for major_id, department_id, name, degree, years, source_id in MAJORS])
    write_csv(os.path.join(args.output_dir, "courses.csv"),
              ["courseId", "departmentId", "courseName", "credits", "lectureHours",
               "practiceHours", "courseType", "sourceYear", "sourceId", "sourceUrl", "active"],
              [(course_id, department_id, name, credits, lecture, practice, course_type,
                source_year[source_id], source_id, source_url[source_id], "true")
               for course_id, department_id, name, credits, lecture, practice, course_type,
               source_id, _ in COURSES])
    write_csv(os.path.join(args.output_dir, "major_courses.csv"),
              ["majorId", "courseId", "recommendedYear", "recommendedSemester", "required"],
              [(major_id, course[0], year, semester, str(bool(required)).lower())
               for course in COURSES for major_id, year, semester, required in course[8]])
    print("Generated: %d departments, %d majors, %d courses, %d major-course links" %
          (len(departments), len(MAJORS), len(COURSES),
           sum(len(course[8]) for course in COURSES)))


if __name__ == "__main__":
    main()
