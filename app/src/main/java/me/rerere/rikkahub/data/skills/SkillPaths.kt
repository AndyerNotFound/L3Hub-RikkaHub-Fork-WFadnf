package me.rerere.rikkahub.data.skills

import me.rerere.workspace.HostPaths
import java.io.File

/**
 * 技能目录内的路径解析。归属判断统一交给 [HostPaths]：realpath 之后按目录边界比较，
 * 软链指到技能目录外面时按解析失败处理。
 */
internal object SkillPaths {
    fun resolveSkillDir(skillsRoot: File, skillName: String): File? {
        if (skillName.isBlank()) return null
        if (skillName == "." || skillName == "..") return null
        if (skillName.contains('/') || skillName.contains('\\')) return null
        return HostPaths.resolve(skillsRoot, skillName)
    }

    fun resolveSkillFile(skillDir: File, relativePath: String): File? {
        if (relativePath.isBlank()) return null
        return HostPaths.resolve(skillDir, relativePath)
    }
}
