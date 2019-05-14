import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main2 {
    private static final Logger logger = LoggerFactory.getLogger(Main2.class);

    // 公用属性
    private CloseableHttpClient httpClient = HttpClientBuilder.create().build();//两个方法中不会同时使用该属性，所以可以定义在此

    // 一级搜索
    private final String oneKeyword = "积木";//一级搜索关键词
    private final String liexing = "3";//店铺类型 所有(1) 天猫(2) 淘宝(3) 淘宝分级(4)
    private final Boolean isTMFlagship = true;//天猫是否只查找旗舰店 在"1-所有" "2-天猫" 模式下有效
    private final String jibie = "huang";//淘宝店级别 金冠(jin) 皇冠(huang) 钻级(zhuan) 心级(xin)
    private final Integer onePageNo = 10;//一级搜索页数
    private Collection<Map<String, String>> dpInfoList_all = new ArrayList<>();//一级搜索获取到的所有店铺的相关信息
    private final String oneFileName = "one.pro";
    private HashMap<String, String> oneHeadDataMap = new HashMap<>();
    private String oneUrl = null;//一级搜索请求URL

    // 二级搜索
    private final String twoKeyword = "积木";//二级搜索关键词
    private Collection<Map<String, String>> spInfoList_all = new ArrayList<>();//二级搜索获取到的该店铺中的所有商品的链接
    private final Integer searchPageNo = 1000;// 二级搜索页数
    private final Integer minPrice = 20;
    private final Integer maxPrice = 200;
    private final String tbOrderType = "hotsell_desc"; // 淘宝店铺排序方式：综合排序-coefp_desc  销量-hotsell_desc 新品-newOn_desc 收藏-hotkeep_desc 价格升序-price_asc 价格降序-price_desc
    private final String tmOrderType = "hotsell_desc"; // 天猫店铺排序方式：默认排序-defaultSort 销量-hotsell_desc 新品-newOn_desc 收藏-hotkeep_desc 价格升序-price_asc 价格降序-price_desc 口碑-koubei
    private final String twoFileName = "two.pro";
    private HashMap<String, String> twoHeadDataMap = new HashMap<>();
    private String twoUrl = null;//二级搜索请求URL

    public Main2() throws Exception {
        BufferedReader bfReader = null;
        try {
            // TODO array改成有序集合
            // 加载配置文件数据到Map
            String[] ss = new String[2];
            ss[0] = oneFileName;
            ss[1] = twoFileName;

            Map[] maps = new Map[2];
            maps[0] = oneHeadDataMap;
            maps[1] = twoHeadDataMap;

            for (int i = 0; i < ss.length; i++) {
                bfReader = new BufferedReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream(ss[i])));
                String line = null;
                Boolean firrstLineflag = true; // 第一行 一定要为请求URL
                while ((line = bfReader.readLine()) != null) {
                    // 注释行舍去
                    line = line.trim();
                    if (line.startsWith("#")) {
                        continue;
                    }

                    if (firrstLineflag) {
                        String url = line.split(" ")[1];
                        // TODO oneFileName可以存入dataMap，在使用前直接去DataMap中取，移除xxxFileName属性
                        if (ss[i].equals(oneFileName)) {
                            // 因为URL中有些key，是根据店铺类型等实际参数变化而变化，要用到具体数据，所以param替换放到发送请求之前进行
                            oneUrl = url;
                        } else if (ss[i].equals(twoFileName)) {
                            twoUrl = url;
                        }
                        firrstLineflag = false;
                        continue;
                    }

                    line = line.replaceAll("\\s|\\n|\\t", "");
                    // 判断改行数据是不是合法的xxx:xxx
                    if (line.matches("\\S+:\\S+")) {
                        Matcher matcher = Pattern.compile("((^.*?):)(.*)").matcher(line);
                        while (matcher.find()) {
                            maps[i].put(matcher.group(2), matcher.group(3));
                        }
                    } else {
                        if (!"".equals(line)) {
                            throw new Exception("配置文件数据不合法\t" + ss[i] + "\tline");
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (bfReader != null) {
                    bfReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String[] args) throws Exception {
        new Main2().begin();
    }

    private void begin() throws Exception {
        searchDpUrl();
        searchSpUrl();
        if(httpClient!=null) {
            httpClient.close();
        }
    }


    /**
     * 获取店铺的链接
     */
    private void searchDpUrl() throws Exception {
        CloseableHttpResponse response = null;
        String url = replaceOneUrl(oneUrl);
        try {
            for (int i = 0; i < onePageNo; i++) {
                HttpGet httpGet = new HttpGet(url + "&s=" + i * 20);// 加上分页
                buildHeaderByMap(httpGet, oneHeadDataMap);
                response = httpClient.execute(httpGet);
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        String string = EntityUtils.toString(responseEntity);
                        // 解析本页中的店铺信息存入List
                        List<Map<String, String>> dpInfoList_temp = getAllDpInfo(string);
                        Iterator<Map<String, String>> iterator = dpInfoList_temp.iterator();
                        while (iterator.hasNext()) {
                            Map<String, String> dpInfoMap = iterator.next();
                            // 天猫店铺只查找旗舰店的逻辑
                            if (isTMFlagship) {
                                // 只要天猫旗舰店，只在 一级搜索类型为所有(1)|天猫(2)时才生效
                                if ("1".equals(liexing) || "2".equals(liexing)) {
                                    if (dpInfoMap.get("dpType").equals("tm")) {
                                        if (!dpInfoMap.get("dpName").endsWith("旗舰店")) {
                                            iterator.remove();
                                            logger.debug("天猫店铺[{}]不是旗舰店，已被移除", dpInfoMap.get("dpName"));
                                            continue;
                                        }
                                    }
                                }
                            }
                        }
                        dpInfoList_all.addAll(dpInfoList_temp);
                    }
                }

                if (response != null) {
                    response.close();
                }
            }
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 获取店铺中商品的链接
     */
    private void searchSpUrl() throws Exception {
        CloseableHttpResponse response = null;
        try {
            // 遍历店铺集合，获取某一店铺中的商品
            for (Map<String, String> dpInfoMap : dpInfoList_all) {
                String url = replaceTwoUrl(twoUrl, dpInfoMap);
                Integer realityPageNo = 1;//实际查询的总页数。可能搜索到100，单只取50；或者想要50，但只搜到20页。
                List<Map<String, String>> spInfoList = null;//存储一个店铺中所有商品的信息
                for (int pageNo = 1; pageNo <= realityPageNo; pageNo++) {
                    HttpGet httpGet = new HttpGet(url + "&pageNo=" + pageNo);
                    buildHeaderByMap(httpGet, twoHeadDataMap);
                    response = httpClient.execute(httpGet);
                    HttpEntity responseEntity = response.getEntity();
                    if (responseEntity != null) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            String bodyString = EntityUtils.toString(responseEntity).replaceAll("\\s|\\n|\\t", "");
                            // 去除推荐商品（淘宝搜索不到时会出现，天猫一直都有）
                            bodyString = bodyString.replaceAll("((其他人还购买了)|(本店内推荐))[\\s\\S]*", "");
                            // 获取第一页数据时，找下总页数
                            if (pageNo == 1) {
                                // 获取搜到的总页数，淘宝天猫获取的方式不同
                                Matcher matcher = Pattern.compile("(page-info.*?/(\\d+?)<)|(ui-page-s-len.*?/(\\d+?)<)").matcher(bodyString);
                                if (matcher.find()) {
                                    if (matcher.group(2) != null) {
                                        realityPageNo = Integer.valueOf(matcher.group(2));
                                    } else if (matcher.group(4) != null) {
                                        realityPageNo = Integer.valueOf(matcher.group(4));
                                    }
                                    // 如果要搜索的页数，小于搜到的总页数，则使用要搜索的页数
                                    if (searchPageNo < realityPageNo) {
                                        realityPageNo = searchPageNo;
                                    }
                                }
                            }
                            spInfoList = getAllSpInfo(bodyString, dpInfoMap);
                        }
                    }
                    if (response != null) {
                        response.close();
                    }
                }
                spInfoList_all.addAll(spInfoList);// 将本店查询到的所有商品链接加入总商品链接集合
                logger.info("店铺[{}] 搜索关键词[{}] 价格范围[{}-{}] 实际页数[{}] 找到{}个商品",dpInfoMap.get("dpName"), twoKeyword, minPrice, maxPrice, realityPageNo, spInfoList.size());
            }

            // 打印商品url
            for (Map<String, String> spInfoMap : spInfoList_all) {
                System.out.println("https://" + spInfoMap.get("spUrl"));
            }
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 使用Map中的数据构建Header
    private void buildHeaderByMap(HttpRequestBase httpRequestBase, Map<String, String> map) {
        Iterator iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next().toString();
            httpRequestBase.addHeader(new BasicHeader(key, map.get(key)));
        }
    }

    // 根据一级搜索配置的条件，更换URL中的参数
    private String replaceOneUrl(String url) throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("q", URLEncoder.encode(oneKeyword, "utf-8"));
        switch (liexing) { //店铺类型 所有(1) 天猫(2) 淘宝(3) 淘宝分级(4)
            case "1":
                map.put("data-key", "shop_type%2Cisb%2Cratesum");
                map.put("data-value", "%2C%2C");
                map.put("isb", "");
                map.put("ratesum", "");
                url = addOrReplaceUrlParam(url, map);
                break;
            case "2":
                map.put("data-key", "isb%2Cshop_type%2Cratesum%2Cgoodrate");
                map.put("data-value", "1%2C%2C%2C");
                map.put("isb", "1");
                map.put("ratesum", "");
                url = addOrReplaceUrlParam(url, map);
                break;
            case "3":
                map.put("data-key", "isb%2Cshop_type%2Cratesum");
                map.put("data-value", "0%2C%2C");
                map.put("isb", "0");
                map.put("ratesum", "");
                url = addOrReplaceUrlParam(url, map);
                break;
            case "4":
                map.put("data-key", "isb%2Cshop_type%2Cratesum");
                map.put("data-value", "0%2C%2C" + jibie);
                map.put("isb", "0");
                map.put("ratesum", jibie);
                url = addOrReplaceUrlParam(url, map);
                break;
        }
        return url;
    }

    // 根据二级搜索配置的条件，更换URL中的店铺URL及参数
    private String replaceTwoUrl(String url, Map<String, String> dpDataMap) throws Exception {
        String dpUrl = dpDataMap.get("dpUrl");
        String dpType = dpDataMap.get("dpType");
        if ("tm".equals(dpType)) {//天猫店铺需获取真正的店铺URL
            dpUrl = transformUrlTbToTm(dpUrl);
            // 记录转换后的URL到dpDataMap
            dpDataMap.put("dpUrl", dpUrl);
        }

        // 替换要搜索店铺的URL
        url = url.replaceFirst("https://\\S+?.com", dpUrl);

        // 替换请求参数
        // TODO 检查需要替换的参数
        Map<String, String> map = new HashMap<>();

        map.put("keyword", URLEncoder.encode(twoKeyword, "gb2312"));
        map.put("lowPrice", String.valueOf(minPrice));
        map.put("highPrice", String.valueOf(maxPrice));
        // 排序参数是一个key根据店铺类型的不同设置不同的
        if ("tb".equals(dpType)) {
            map.put("orderType", tbOrderType);
        } else if ("tm".equals(dpType)) {
            map.put("orderType", tmOrderType);
        }
        return addOrReplaceUrlParam(url, map);
    }

    // 将天猫店铺的淘宝URL转换为天猫URL（商品数据时从天猫URL中获取的）
    private String transformUrlTbToTm(String dpUrl) throws Exception {
        // TODO

        // 判断域名是否属于天猫
        if (false) { // 属于天猫说明该URL已经被转换过了
            return dpUrl;
        }
        // 请求该URL，解析响应数据获取转换结果
        CloseableHttpResponse response = null;
        HttpGet httpGet = new HttpGet(dpUrl);
        // 设置请求头信息
        try {
            response = httpClient.execute(httpGet);
        } catch (ClientProtocolException e) {
            String errorMsg = e.getCause().getMessage();
            errorMsg = errorMsg.replaceAll("\\s|\\n|\\t", "");

            Matcher matcher = Pattern.compile("https://\\S+?.com").matcher(errorMsg);
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        return dpUrl;
    }

    // 处理请求参数，将Url中给定的pName设置成pValue，Url中不存在pName时，为其添加
    private String addOrReplaceUrlParam(String url, Map<String, String> map) {
        Iterator<String> iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            String value = map.get(key);
            if (url.contains(key + "=")) {
                // 替换参数
                url = url.replaceFirst(key + "=.*?(&|$)", key + "=" + value + "&");
            } else {
                // 新增参数
                if (url.endsWith("&")) {
                    url += key + "=" + value;
                } else {
                    url += "&" + key + "=" + value;
                }
            }
        }
        return url.endsWith("&") ? url.substring(0, url.length() - 1) : url;
    }

    // 解析本页数据，获取所有店铺的类型(tm tb)、店铺名、旺旺名、URL
    private List<Map<String, String>> getAllDpInfo(String bodyString) {
        List<Map<String, String>> list = new ArrayList<>();
        bodyString = bodyString.replaceAll("\\s|\\n|\\t", "");
        Matcher matcher = Pattern.compile("\"title\":\"(\\S{1,28}?)\",\"nick\":\"(\\S{1,28}?)\"\\S+?shopUrl\":\"//(\\S+?)\"\\S+?\"shopIcon\":\\{(\\S+?)\\}").matcher(bodyString);
        while (matcher.find()) {
            Map<String, String> map = new HashMap<String, String>();
            // 店铺名
            String dpName = matcher.group(1);
            map.put("dpName", dpName);
            // 旺旺名
            String wwName = matcher.group(2);
            map.put("wwName", wwName);
            // 店铺首页URL
            String dpUrl = matcher.group(3);
            map.put("dpUrl", "https://" + dpUrl);
            String dpType = matcher.group(4);
            if (dpType.contains("\"title\":\"天猫\"")) {
                map.put("dpType", "tm");
            } else {
                map.put("dpType", "tb");
            }
            list.add(map);
            logger.debug("店铺类型{} 店铺名{} 旺旺名{} 店铺链接{}", dpType, dpName, wwName, dpUrl);
        }
        return list;
    }

    // 解析本页数据，获取所有商品的标题、价格、URL
    private List<Map<String, String>> getAllSpInfo(String bodyString, Map<String, String> dpInfoMap) {
        List<Map<String, String>> spInfoList = new ArrayList<>();
        //获取当页所有商品的标题、链接、价格
        String rstr_tb = "imgalt=\\\\\\\"(\\S+?)\\\\\\\".*?(item\\.taobao\\.com/item\\.htm\\?id=\\d+).*?c-price\\\\\\\">(\\d+)";
        String rstr_tm = "imgalt=\\\\\\\"(\\S+?)\\\\\\\".*?(detail\\.tmall\\.com/item\\.htm\\?id=\\d+).*?c-price\\\\\\\">(\\d+)";
        Matcher matcher = null;
        if ("tb".equals(dpInfoMap.get("dpType"))) {
            matcher = Pattern.compile(rstr_tb).matcher(bodyString);
        } else if ("tm".equals(dpInfoMap.get("dpType"))) {
            matcher = Pattern.compile(rstr_tm).matcher(bodyString);
        }
        while (matcher.find()) {
            // 剔除高亮标签
            String spName = matcher.group(1).replaceAll("<spanclass=H>", "").replaceAll("</span>", "");
            String spUrl = matcher.group(2);
            String spPrice = matcher.group(3);
            Map<String, String> spInfoMap = new HashMap<>();
            spInfoMap.put("spName", spName);
            spInfoMap.put("spUrl", spUrl);
            spInfoMap.put("spPrice", spPrice);
            spInfoList.add(spInfoMap);
        }
        return spInfoList;
    }
}
