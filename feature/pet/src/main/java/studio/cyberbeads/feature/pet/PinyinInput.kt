package studio.cyberbeads.feature.pet

import android.content.Context

/** Local candidates only: unfinished spelling never leaves the device. */
internal class PinyinInput(context:Context){
    data class Candidate(val text:String,val consumed:Int)
    private val syllables=context.assets.open("pinyin-v4.txt").bufferedReader().useLines{lines->lines.associate{val p=it.split('=',limit=2);p[0] to p[1]}}
    private val common=mapOf("ni" to "你呢泥拟妮", "hao" to "好号浩豪", "wo" to "我握窝", "shi" to "是时事十使世市师试诗", "de" to "的得德", "ma" to "吗妈马嘛", "ai" to "爱哎矮", "le" to "了乐", "men" to "们门", "zai" to "在再", "xiang" to "想像向香", "tian" to "天甜田", "hen" to "很狠", "kai" to "开凯", "xin" to "心新信", "xie" to "谢写些", "wan" to "晚玩万完", "an" to "安按", "chong" to "宠冲虫", "wu" to "物五无午", "ba" to "吧把八", "bu" to "不步布", "ya" to "呀牙鸭", "chi" to "吃迟持", "fan" to "饭反凡", "qing" to "请轻情清", "kan" to "看砍刊")
    private val phrases=mapOf("nihao" to "你好", "nihaoya" to "你好呀", "xiexie" to "谢谢", "zaijian" to "再见", "wanan" to "晚安", "zaoshanghao" to "早上好", "woxiangni" to "我想你", "woaini" to "我爱你", "jintian" to "今天", "kaixin" to "开心", "baobao" to "抱抱", "chifan" to "吃饭", "chongwu" to "宠物", "keai" to "可爱", "meiguanxi" to "没关系", "duibuqi" to "对不起", "nihaoma" to "你好吗")
    private val cache=object:LinkedHashMap<String,List<Candidate>>(128,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,List<Candidate>>?)=size>128}
    private val extra=mapOf("wo" to "我", "ni" to "你", "wojintian" to "我今天", "woxiang" to "我想", "nizaiganma" to "你在干嘛", "woxiangnile" to "我想你了", "jintianhenkaixin" to "今天很开心", "woyao" to "我要", "bukaixin" to "不开心", "xihuan" to "喜欢", "woxihuanni" to "我喜欢你", "zenmele" to "怎么了", "weishenme" to "为什么", "meishenme" to "没什么", "haode" to "好的", "keyi" to "可以", "bukeyi" to "不可以", "zhidaole" to "知道了", "zhende" to "真的", "mingtian" to "明天", "zuotian" to "昨天", "gongzuo" to "工作", "xuexi" to "学习", "shuijiao" to "睡觉", "chiqu" to "出去", "yiqi" to "一起", "women" to "我们", "pengyou" to "朋友", "zaoan" to "早安", "xiawuhao" to "下午好", "zhongwuhao" to "中午好", "wanshanghao" to "晚上好", "tianqi" to "天气", "xianzai" to "现在", "ganjue" to "感觉", "youyidian" to "有一点", "leile" to "累了", "haokun" to "好困", "haoe" to "好饿", "haokeai" to "好可爱", "peipeiwo" to "陪陪我", "jianggegushi" to "讲个故事", "jianggexiaohua" to "讲个笑话", "nihaoa" to "你好啊", "xiexieni" to "谢谢你", "bukeqi" to "不客气", "buzhidao" to "不知道", "wozhidao" to "我知道", "zhenbang" to "真棒", "jiayou" to "加油", "meiwenti" to "没问题", "yihouer" to "一会儿", "shenme" to "什么", "ruhe" to "如何", "shijian" to "时间")
    @Synchronized fun candidates(spelling:String):List<Candidate>{
        val s=spelling.lowercase().replace("'","")
        if(s.isEmpty())return emptyList();cache[s]?.let{return it}
        val output=mutableListOf<Candidate>()
        val all=phrases+extra;all.keys.filter{s.startsWith(it)}.sortedByDescending{it.length}.forEach{k->output+=Candidate(all.getValue(k),k.length)}
        val keys=syllables.keys.filter{s.startsWith(it)}.sortedByDescending{it.length}.take(5)
        keys.forEach{k->(common[k].orEmpty()+syllables.getValue(k)).filter{it in '\u4e00'..'\u9fff'}.toList().distinct().forEach{output+=Candidate(it.toString(),k.length)}}
        if(output.isEmpty())syllables.keys.filter{it.startsWith(s)}.sortedWith(compareBy<String>{if(it in common)0 else 1}.thenBy{it.length}).take(5).forEach{k->(common[k].orEmpty()+syllables.getValue(k)).filter{it in '\u4e00'..'\u9fff'}.take(12).forEach{output+=Candidate(it.toString(),s.length)}}
        all.filterKeys{it.startsWith(s)&&it!=s}.entries.take(6).forEach{(k,v)->output+=Candidate(v,s.length)}
        return output.distinctBy{it.text}.take(180).also{cache[s]=it}
    }
}
