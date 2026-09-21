package com.txt2epub.app.core

import com.txt2epub.app.model.ChapterRule

/**
 * 内置章节识别规则库。用户可以在「规则管理」里开关、调整层级、删除，也可以新增自定义正则。
 */
object BuiltInRules {

    private const val CN_NUM = "[0-9零一二三四五六七八九十百千万两]{1,12}"

    fun defaults(): List<ChapterRule> = listOf(
        ChapterRule(
            id = "bi_volume",
            name = "中文 · 第X卷",
            pattern = """^\s*第\s*${CN_NUM}\s*卷.*$""",
            level = 1, builtIn = true, order = 10
        ),
        ChapterRule(
            id = "bi_chapter",
            name = "中文 · 第X章 / 第X回",
            pattern = """^\s*第\s*${CN_NUM}\s*[章回].*$""",
            level = 1, builtIn = true, order = 20
        ),
        ChapterRule(
            id = "bi_section",
            name = "中文 · 第X节",
            pattern = """^\s*第\s*${CN_NUM}\s*节.*$""",
            level = 2, builtIn = true, order = 30
        ),
        ChapterRule(
            id = "bi_part",
            name = "中文 · 第X篇 / 部 / 集 / 幕",
            pattern = """^\s*第\s*${CN_NUM}\s*[篇部集幕折].*$""",
            level = 1, builtIn = true, order = 40
        ),
        ChapterRule(
            id = "bi_english",
            name = "英文 · Chapter / Part / Book",
            pattern = """^\s*(chapter|chap\.?|part|book|section)\s+([0-9]{1,5}|[IVXLCDM]{1,7})\b.*$""",
            level = 1, builtIn = true, order = 50
        ),
        ChapterRule(
            id = "bi_numeric",
            name = "数字编号 · 1. / 1、 / 1：",
            pattern = """^\s*[0-9]{1,4}\s*[.、．，,：:]\s*\S.{0,39}$""",
            level = 1, builtIn = true, enabled = false, order = 60
        ),
        ChapterRule(
            id = "bi_special",
            name = "序 / 楔子 / 后记 / 番外",
            pattern = """^\s*(楔子|序言|小序|序|引言|引子|前言|自序|后记|尾声|终章|番外|附录|题记|内容简介|主要人物)\s*[:：]?.*$""",
            level = 1, builtIn = true, order = 70
        ),
        ChapterRule(
            id = "bi_paren",
            name = "括号编号 · (一) / （1）",
            pattern = """^\s*[（(]\s*[0-9零一二三四五六七八九十]{1,6}\s*[)）]\s*\S.*$""",
            level = 2, builtIn = true, enabled = false, order = 80
        ),
        ChapterRule(
            id = "bi_cnnum",
            name = "中文数字 · 一、 / 二.",
            pattern = """^\s*[一二三四五六七八九十]{1,4}\s*[、．.：:]\s*\S.{0,39}$""",
            level = 2, builtIn = true, enabled = false, order = 90
        ),
        ChapterRule(
            id = "bi_mark",
            name = "符号编号 · 【001】 / [1]",
            pattern = """^\s*[【\[]\s*[0-9零一二三四五六七八九十百]{1,6}\s*[\]】]\s*\S.*$""",
            level = 1, builtIn = true, enabled = false, order = 100
        ),
    )
}
