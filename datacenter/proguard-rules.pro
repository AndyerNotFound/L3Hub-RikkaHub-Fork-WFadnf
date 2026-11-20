# 进程间协议与数据中心的元数据都靠 kotlinx.serialization 的生成序列化器读写，
# 类名会出现在 JSON 里（枚举名、字段名），别让 R8 改名。
-keep class com.l3hub.syncproto.** { *; }
-keep class com.l3hub.database.wfadnf.core.** { *; }
