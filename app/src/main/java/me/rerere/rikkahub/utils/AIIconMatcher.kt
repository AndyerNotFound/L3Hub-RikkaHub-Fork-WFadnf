package me.rerere.rikkahub.utils

import android.content.Context

private var assetIconNamesCache: List<String>? = null

fun loadAIIconNames(context: Context): List<String> {
    assetIconNamesCache?.let { return it }
    return context.assets.list("icons").orEmpty().toList().also { assetIconNamesCache = it }
}

private val iconCache = mutableMapOf<String, String>()

// https://lobehub.com/zh/icons
fun computeAIIconByName(name: String, availableIcons: List<String> = emptyList()): String? {
    iconCache[name]?.let { return it }

    val lowerName = name.lowercase()
    val result = when {
        PATTERN_OPENAI.containsMatchIn(lowerName) -> "openai.svg"
        PATTERN_GEMINI.containsMatchIn(lowerName) -> "gemini-color.svg"
        PATTERN_GOOGLE.containsMatchIn(lowerName) -> "google-color.svg"
        PATTERN_CLAUDE.containsMatchIn(lowerName) -> "claude-color.svg"
        PATTERN_ANTHROPIC.containsMatchIn(lowerName) -> "anthropic.svg"
        PATTERN_DEEPSEEK.containsMatchIn(lowerName) -> "deepseek-color.svg"
        PATTERN_GROK.containsMatchIn(lowerName) -> "xai.svg"
        PATTERN_QWEN.containsMatchIn(lowerName) -> "qwen-color.svg"
        PATTERN_DOUBAO.containsMatchIn(lowerName) -> "doubao-color.svg"
        PATTERN_OPENROUTER.containsMatchIn(lowerName) -> "openrouter.svg"
        PATTERN_ZHIPU.containsMatchIn(lowerName) -> "zhipu-color.svg"
        PATTERN_MISTRAL.containsMatchIn(lowerName) -> "mistral-color.svg"
        PATTERN_META.containsMatchIn(lowerName) -> "meta-color.svg"
        PATTERN_HUNYUAN.containsMatchIn(lowerName) -> "hunyuan-color.svg"
        PATTERN_GEMMA.containsMatchIn(lowerName) -> "gemma-color.svg"
        PATTERN_PERPLEXITY.containsMatchIn(lowerName) -> "perplexity-color.svg"
        PATTERN_ALIYUN.containsMatchIn(lowerName) -> "alibabacloud-color.svg"
        PATTERN_BYTEDANCE.containsMatchIn(lowerName) -> "bytedance-color.svg"
        PATTERN_SILLICON_CLOUD.containsMatchIn(lowerName) -> "siliconflow.svg"
        PATTERN_AIHUBMIX.containsMatchIn(lowerName) -> "aihubmix-color.svg"
        PATTERN_NAAPI.containsMatchIn(lowerName) -> "naapi-color.svg"
        PATTERN_OLLAMA.containsMatchIn(lowerName) -> "ollama.svg"
        PATTERN_GITHUB.containsMatchIn(lowerName) -> "github.svg"
        PATTERN_CLOUDFLARE.containsMatchIn(lowerName) -> "cloudflare-color.svg"
        PATTERN_MINIMAX.containsMatchIn(lowerName) -> "minimax-color.svg"
        PATTERN_XAI.containsMatchIn(lowerName) -> "xai.svg"
        PATTERN_JUHENEXT.containsMatchIn(lowerName) -> "juhenext.png"
        PATTERN_KIMI.containsMatchIn(lowerName) -> "kimi-color.svg"
        PATTERN_MOONSHOT.containsMatchIn(lowerName) -> "moonshot.svg"
        PATTERN_302.containsMatchIn(lowerName) -> "302ai.svg"
        PATTERN_STEP.containsMatchIn(lowerName) -> "stepfun-color.svg"
        PATTERN_INTERN.containsMatchIn(lowerName) -> "internlm-color.svg"
        PATTERN_COHERE.containsMatchIn(lowerName) -> "cohere-color.svg"
        PATTERN_TAVERN.containsMatchIn(lowerName) -> "tavern.png"
        PATTERN_CEREBRAS.containsMatchIn(lowerName) -> "cerebras-color.svg"
        PATTERN_NVIDIA.containsMatchIn(lowerName) -> "nvidia-color.svg"
        PATTERN_PPIO.containsMatchIn(lowerName) -> "ppio-color.svg"
        PATTERN_VERCEL.containsMatchIn(lowerName) -> "vercel.svg"
        PATTERN_GROQ.containsMatchIn(lowerName) -> "groq.svg"
        PATTERN_TOKENPONY.containsMatchIn(lowerName) -> "tokenpony.svg"
        PATTERN_LING.containsMatchIn(lowerName) -> "ling.png"
        PATTERN_MIMO.containsMatchIn(lowerName) -> "xiaomimimo.svg"
        PATTERN_LONGCAT.containsMatchIn(lowerName) -> "longcat-color.svg"
        PATTERN_ELEVENLABS.containsMatchIn(lowerName) -> "elevenlabs.svg"
        PATTERN_FISH_AUDIO.containsMatchIn(lowerName) -> "fish-audio.svg"
        PATTERN_ERNIE.containsMatchIn(lowerName) -> "wenxin.svg"
        PATTERN_NOVA.containsMatchIn(lowerName) -> "nova.svg"
        PATTERN_PHI.containsMatchIn(lowerName) -> "microsoft.svg"
        PATTERN_SOLAR.containsMatchIn(lowerName) -> "upstage.svg"
        PATTERN_JAMBA.containsMatchIn(lowerName) -> "ai21.svg"
        PATTERN_DBRX.containsMatchIn(lowerName) -> "dbrx.svg"
        PATTERN_LLAVA.containsMatchIn(lowerName) -> "llava.svg"
        PATTERN_HERMES.containsMatchIn(lowerName) -> "nousresearch.svg"
        PATTERN_DOLPHIN.containsMatchIn(lowerName) -> "dolphin.svg"
        PATTERN_AYA.containsMatchIn(lowerName) -> "aya.svg"
        PATTERN_GRANITE.containsMatchIn(lowerName) -> "ibm.svg"
        PATTERN_SNOWFLAKE.containsMatchIn(lowerName) -> "snowflake.svg"
        PATTERN_SPARK.containsMatchIn(lowerName) -> "spark.svg"
        PATTERN_BAICHUAN.containsMatchIn(lowerName) -> "baichuan.svg"
        PATTERN_SENSENOVA.containsMatchIn(lowerName) -> "sensenova.svg"
        PATTERN_SKYWORK.containsMatchIn(lowerName) -> "skywork.svg"
        PATTERN_TIANGONG.containsMatchIn(lowerName) -> "tiangong.svg"
        PATTERN_YUANBAO.containsMatchIn(lowerName) -> "yuanbao.svg"
        PATTERN_XUANYUAN.containsMatchIn(lowerName) -> "xuanyuan.svg"
        PATTERN_PALM.containsMatchIn(lowerName) -> "palm.svg"
        PATTERN_SORA.containsMatchIn(lowerName) -> "sora.svg"
        PATTERN_CODEX.containsMatchIn(lowerName) -> "codex.svg"
        PATTERN_YI.containsMatchIn(lowerName) -> "yi.svg"
        PATTERN_MISTRAL_VARIANT.containsMatchIn(lowerName) -> "mistral-color.svg"
        PATTERN_NEMOTRON.containsMatchIn(lowerName) -> "nvidia-color.svg"
        PATTERN_ARCEE.containsMatchIn(lowerName) -> "arcee.svg"
        PATTERN_INFLECTION.containsMatchIn(lowerName) -> "inflection.svg"
        PATTERN_AZURE.containsMatchIn(lowerName) -> "azure.svg"
        PATTERN_RIKKAHUB.containsMatchIn(lowerName) -> "rikkahub.svg"
        PATTERN_SEARCH_LINKUP.containsMatchIn(lowerName) -> "linkup.png"
        PATTERN_SEARCH_BING.containsMatchIn(lowerName) -> "bing.png"
        PATTERN_SEARCH_TAVILY.containsMatchIn(lowerName) -> "tavily.png"
        PATTERN_SEARCH_EXA.containsMatchIn(lowerName) -> "exa.png"
        PATTERN_SEARCH_BRAVE.containsMatchIn(lowerName) -> "brave.svg"
        PATTERN_SEARCH_METASO.containsMatchIn(lowerName) -> "metaso.svg"
        PATTERN_SEARCH_FIRECRAWL.containsMatchIn(lowerName) -> "firecrawl.svg"
        PATTERN_SEARCH_JINA.containsMatchIn(lowerName) -> "jina.svg"
        PATTERN_SEARCH_SEARXNG.containsMatchIn(lowerName) -> "searxng.svg"
        else -> null
    }

    val icon = (result ?: matchIconByAssetName(lowerName, availableIcons))
        ?.let { preferColorIcon(it, availableIcons) }
    icon?.let { iconCache[name] = it }
    return icon
}

/** 同一图标存在彩色版本时优先使用彩色版本。 */
private fun preferColorIcon(icon: String, availableIcons: List<String>): String {
    if (availableIcons.isEmpty() || icon.contains("-color.")) return icon
    val colorIcon = icon.substringBeforeLast('.') + "-color.svg"
    return if (colorIcon in availableIcons) colorIcon else icon
}

private val GENERIC_ICON_NAMES = setOf(
    "api", "app", "chat", "new", "text", "color", "brand", "cloud", "code", "coder",
    "model", "bot", "lab", "labs", "data", "dev", "web", "tool", "image", "video",
    "audio", "search", "dark", "light", "open", "free", "beta", "pro", "max", "mini",
    "plus", "base", "tech", "one", "two", "top", "hub", "mcp", "sdk", "the", "and",
)

private fun matchIconByAssetName(name: String, availableIcons: List<String>): String? {
    if (availableIcons.isEmpty()) return null
    val words = name.split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in GENERIC_ICON_NAMES }
        .toSet()
    if (words.isEmpty()) return null

    val candidates = availableIcons.mapNotNull { file ->
        if (!file.endsWith(".svg") && !file.endsWith(".png")) return@mapNotNull null
        val base = file.substringBeforeLast('.')
            .removeSuffix("-color")
            .removeSuffix("-brand")
            .removeSuffix("-text")
        if (base.length < 3 || base in GENERIC_ICON_NAMES || base.startsWith("dark-")) {
            null
        } else {
            base to file
        }
    }

    candidates.firstOrNull { (base, _) -> base in words }?.let { return it.second }
    // 长名字才做包含匹配，避免 ace 这类短名字误伤 space
    return candidates
        .filter { (base, _) -> base.length >= 5 && name.contains(base) }
        .maxByOrNull { it.first.length }
        ?.second
}

private val PATTERN_RIKKAHUB = Regex("rikka|auto")
private val PATTERN_OPENAI = Regex("(gpt|openai|o\\d)")
private val PATTERN_GEMINI = Regex("(gemini|nano-banana)")
private val PATTERN_GOOGLE = Regex("google")
private val PATTERN_ANTHROPIC = Regex("anthropic")
private val PATTERN_CLAUDE = Regex("claude")
private val PATTERN_DEEPSEEK = Regex("deepseek")
private val PATTERN_GROK = Regex("grok")
private val PATTERN_QWEN = Regex("qwen|qwq|qvq|\\bwanx?[\\d-]")
private val PATTERN_DOUBAO = Regex("doubao|豆包|火山")
private val PATTERN_OPENROUTER = Regex("openrouter")
private val PATTERN_ZHIPU = Regex("zhipu|智谱|glm")
private val PATTERN_MISTRAL = Regex("mistral")
private val PATTERN_META = Regex("meta\\b|(?<!o)llama|muse")
private val PATTERN_HUNYUAN = Regex("hunyuan|tencent|\\bhy(\\b|\\d)")
private val PATTERN_GEMMA = Regex("gemma")
private val PATTERN_PERPLEXITY = Regex("perplexity")
private val PATTERN_BYTEDANCE = Regex("bytedance|火山")
private val PATTERN_ALIYUN = Regex("aliyun|阿里云|百炼")
private val PATTERN_SILLICON_CLOUD = Regex("silicon|硅基")
private val PATTERN_AIHUBMIX = Regex("aihubmix")
private val PATTERN_NAAPI = Regex("naapi|钠api|鈉api")
private val PATTERN_OLLAMA = Regex("ollama")
private val PATTERN_GITHUB = Regex("github")
private val PATTERN_CLOUDFLARE = Regex("cloudflare")
private val PATTERN_MINIMAX = Regex("minimax")
private val PATTERN_XAI = Regex("xai")
private val PATTERN_JUHENEXT = Regex("juhenext")
private val PATTERN_KIMI = Regex("kimi|k3")
private val PATTERN_MOONSHOT = Regex("moonshot|月之暗面")
private val PATTERN_302 = Regex("302")
private val PATTERN_STEP = Regex("step|阶跃")
private val PATTERN_INTERN = Regex("intern|书生")
private val PATTERN_COHERE = Regex("cohere|command-.+")
private val PATTERN_TAVERN = Regex("tavern")
private val PATTERN_CEREBRAS = Regex("cerebras")
private val PATTERN_NVIDIA = Regex("nvidia")
private val PATTERN_PPIO = Regex("ppio|派欧")
private val PATTERN_VERCEL = Regex("vercel")
private val PATTERN_GROQ = Regex("groq")
private val PATTERN_TOKENPONY = Regex("tokenpony|小马算力")
private val PATTERN_LING = Regex("ling|ring|百灵")
private val PATTERN_MIMO = Regex("mimo|xiaomi|小米")
private val PATTERN_LONGCAT = Regex("longcat")
private val PATTERN_ELEVENLABS = Regex("elevenlabs")
private val PATTERN_FISH_AUDIO = Regex("fish.?audio|fishaudio")

private val PATTERN_SEARCH_LINKUP = Regex("linkup")
private val PATTERN_SEARCH_BING = Regex("bing")
private val PATTERN_SEARCH_TAVILY = Regex("tavily")
private val PATTERN_SEARCH_EXA = Regex("exa")
private val PATTERN_SEARCH_BRAVE = Regex("brave")
private val PATTERN_SEARCH_METASO = Regex("metaso|秘塔")
private val PATTERN_SEARCH_FIRECRAWL = Regex("firecrawl")
private val PATTERN_SEARCH_JINA = Regex("jina")
private val PATTERN_SEARCH_SEARXNG = Regex("searxng")

private val PATTERN_ERNIE = Regex("ernie|wenxin|文心|baidu")
private val PATTERN_NOVA = Regex("\\bnova\\b|amazon|bedrock|\\baws\\b")
private val PATTERN_PHI = Regex("\\bphi\\b|phi-|microsoft")
private val PATTERN_SOLAR = Regex("\\bsolar\\b|upstage")
private val PATTERN_JAMBA = Regex("jamba|ai21")
private val PATTERN_DBRX = Regex("dbrx|databricks")
private val PATTERN_LLAVA = Regex("llava")
private val PATTERN_HERMES = Regex("hermes|nous")
private val PATTERN_DOLPHIN = Regex("dolphin")
private val PATTERN_AYA = Regex("\\baya\\b")
private val PATTERN_GRANITE = Regex("granite|\\bibm\\b")
private val PATTERN_SNOWFLAKE = Regex("snowflake|arctic")
private val PATTERN_SPARK = Regex("spark|讯飞|iflytek")
private val PATTERN_BAICHUAN = Regex("baichuan|百川")
private val PATTERN_SENSENOVA = Regex("sensenova|商汤")
private val PATTERN_SKYWORK = Regex("skywork|天工")
private val PATTERN_TIANGONG = Regex("tiangong")
private val PATTERN_YUANBAO = Regex("yuanbao|元宝")
private val PATTERN_XUANYUAN = Regex("xuanyuan|轩辕")
private val PATTERN_PALM = Regex("\\bpalm\\b")
private val PATTERN_SORA = Regex("\\bsora\\b")
private val PATTERN_CODEX = Regex("codex")
private val PATTERN_YI = Regex("(^|[^a-z0-9])yi([^a-z0-9]|$)|零一")
private val PATTERN_MISTRAL_VARIANT = Regex("codestral|magistral|devstral|ministral|pixtral")
private val PATTERN_NEMOTRON = Regex("nemotron")
private val PATTERN_ARCEE = Regex("arcee")
private val PATTERN_INFLECTION = Regex("inflection|\\bpi\\b")
private val PATTERN_AZURE = Regex("azure")
