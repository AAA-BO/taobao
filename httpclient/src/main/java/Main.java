import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.xml.DOMConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // 公用属性
    private CloseableHttpClient httpClient = HttpClientBuilder.create().build();//两个方法中不会同时使用该属性，所以可以定义在此

    // 一级搜索
    private final String oneKeyword = "开发智力玩具";//一级搜索关键词
    private final String liexing = "1";//店铺类型 所有(1) 天猫(2) 淘宝(3) 淘宝分级(4)
    private final Boolean isTMFlagship = false;//天猫是否只查找旗舰店 在"1-所有" "2-天猫" 模式下有效
    private final String jibie = "huang";//淘宝店级别 金冠(jin) 皇冠(huang) 钻级(zhuan) 心级(xin)
    private final Integer onePageNo = 1;//一级搜索页数
    private Collection<Map<String, String>> dpInfoList = null;//一级搜索获取到的所有店铺的相关信息
    private final String oneFileName = "one.pro";
    private HashMap<String, String> oneHeadDataMap = new HashMap<>();
    private String oneUrl = null;//一级搜索请求URL

    // 二级搜索
    private final String twoKeyword = "玩具";//二级搜索关键词
    private Collection<String> spUrlList = new ArrayList<>();//二级搜索获取到的该店铺中的所有商品的链接
    private final Integer beginPageNo = 1;// 二级搜索 从那一页开始搜
    private final Integer searchPageNo = 1000;// 二级搜索页数
    private final Integer minPrice = 20;
    private final Integer maxPrice = 200;
    private final String tbOrderType = "coefp_desc"; // 淘宝店铺排序方式：综合排序-coefp_desc  销量-hotsell_desc 新品-newOn_desc 收藏-hotkeep_desc 价格升序-price_asc 价格降序-price_desc
    private final String tmOrderType = "defaultSort"; // 天猫店铺排序方式：默认排序-defaultSort 销量-hotsell_desc 新品-newOn_desc 收藏-hotkeep_desc 价格升序-price_asc 价格降序-price_desc 口碑-koubei
    private final String twoFileName = "two.pro";
    private HashMap<String, String> twoHeadDataMap = new HashMap<>();
    private String twoUrl = null;//二级搜索请求URL

    // 三级搜索
    private final String threeFileName = "three.pro";
    private HashMap<String, String> threeHeadDataMap = new HashMap<>();
    private String threeUrl = null;
    private final String threeSort = "sale-desc";

    private final Double multiple = 1.5;//最小倍数 // 按销量排序 价格范围为 原价*1.5 - 原价*1.5*10
    private final int passNo = 5;// 查询到的商品有3个就通过
    private final int failPercent = 33; // 在搜索到的商品中，若有33%被舍弃，该店铺就被舍弃


    public Main() throws Exception {
        BufferedReader bfReader = null;
        try {
            // TODO array改成有序集合
            // 加载配置文件数据到Map
            String[] ss = new String[3];
            ss[0] = oneFileName;
            ss[1] = twoFileName;
            ss[2] = threeFileName;

            Map[] maps = new Map[3];
            maps[0] = oneHeadDataMap;
            maps[1] = twoHeadDataMap;
            maps[2] = threeHeadDataMap;

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
                        } else if (ss[i].equals(threeFileName)) {
                            threeUrl = url;
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
        new Main().begin();
    }

    private void begin() throws Exception {
        searchDpUrl();
        searchSpUrl();
    }


    /**
     * 获取店铺的链接
     */
    private void searchDpUrl() throws Exception {
        CloseableHttpResponse response = null;

        // 替换URL参数
        String url = replaceOneUrl(oneUrl);
        try {
            for (int i = 0; i < onePageNo; i++) {
                HttpGet httpGet = new HttpGet(url + "&s=" + i * 20);// 加上分页
                // 设置请求头
                buildHeaderByMap(httpGet, oneHeadDataMap);
                response = httpClient.execute(httpGet);
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    logger.debug("一级搜索\t响应状态码{}", response.getStatusLine().getStatusCode());
                    if (response.getStatusLine().getStatusCode() == 200) {
                        // 获取响应数据 并去除换行空格等空白字符
                        String string = EntityUtils.toString(responseEntity);
                        logger.debug("一级搜索\t响应数据{}", string);
                        // 获取店铺相关信息
                        dpInfoList = getAllDpInfo(string);
                        Iterator<Map<String, String>> iterator = dpInfoList.iterator();
                        while (iterator.hasNext()) {
                            Map<String, String> dpInfoMap = iterator.next();
                            logger.debug("一级搜索\t店铺链接{}名称{}类型{}", dpInfoMap.get("dpUrl"), dpInfoMap.get("dpName"), dpInfoMap.get("dpType"));
                            if (isTMFlagship) {// 针对天猫店铺只获取旗舰店
                                if ("1".equals(liexing) || "2".equals(liexing)) {
                                    if (dpInfoMap.get("dpType").equals("tm")) { // 为天猫店铺
                                        if (!dpInfoMap.get("dpName").endsWith("旗舰店")) {
                                            iterator.remove();
                                            logger.debug("一级搜索\t{}为非旗舰店，已被移除", dpInfoMap.get("dpName"));
                                            continue;
                                        }
                                    }
                                }
                            }
                            logger.info(dpInfoMap.get("dpUrl"));
                        }
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
                if (httpClient != null) {
//                    httpClient.close();
                }
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
        if (dpInfoList == null) {
            throw new Exception("dpInfoList为null");
        }
        try {
            // 遍历所有店铺
            for (Map<String, String> dpInfoMap : dpInfoList) {
                // >>>>>>开始查询某一个店铺中的商品

                // 临时List，用于支持实现排除店铺的逻辑
                Collection<String> spUrlList_temp = new ArrayList<>();
                Integer failNo = 9999;//当排除数量达到该值时，该店铺被排除
                Integer failNo_reality = 0;//实际已经排除的值

                // 替换URL参数，传入店铺信息，用于动态构建url地址、排序方式param
                String url = replaceTwoUrl(twoUrl, dpInfoMap);
                Integer allPageNo = beginPageNo + 1;//总页数，默认为开始页数+1

                Boolean isBreak = false;//是否break，用于跳出多层循环
                // 根据商品页数循环获取每页商品
                for (int pageNo = beginPageNo; !isBreak && pageNo <= allPageNo; pageNo++) {
                    HttpGet httpGet = new HttpGet(url + "&pageNo=" + pageNo);
                    // 设置请求头信息
                    buildHeaderByMap(httpGet, twoHeadDataMap);
                    response = httpClient.execute(httpGet);
                    HttpEntity responseEntity = response.getEntity();
                    if (responseEntity != null) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            String bodyString = EntityUtils.toString(responseEntity).replaceAll("\\s|\\n|\\t", "");
                            // 去除推荐商品 淘宝搜索不到时会出现，天猫一直都有
                            bodyString = bodyString.replaceAll("[其他人还购买了|本店内推荐][\\s\\S]*","");
                            // 获取第一页数据时，找下总页数、搜到商品的总数、并根据搜到商品的总数+舍弃率计算店铺舍弃数量
                            if (pageNo == beginPageNo) {
                                // 搜索到商品总数的计算逻辑为：(总页数-1)*每页条数 + 每页条数*0.5
                                Matcher matcher = Pattern.compile("共搜索到<span>(\\d+?)</span>.*?个符合条件的商品page-info.*?/(\\d+?)<").matcher(bodyString);
                                if (matcher.find()) {
                                    Integer allSpNo = Integer.valueOf(matcher.group(1));//搜索到的商品数
                                    // 去掉前面排除页的商品数

                                    // TODO
                                    // 计算当排除数
                                    failNo = allSpNo * failPercent;

                                    allPageNo = Integer.valueOf(matcher.group(2));//获取总页数
                                    // 如果二级搜索设置总页数小于实际总页数，表示不用搜索所有页
                                    if (searchPageNo < allPageNo) {
                                        allPageNo = searchPageNo;
                                    }
                                }
                            }

                            // 先处理淘宝
                            Matcher matcher = Pattern.compile("imgalt=\\\\\"(\\S+?)<spanclass=H>(\\S+?)</span>(\\S+?)\\\\\"\\S+?\\\\\"item-name.+?(item.taobao.com/item.htm\\?id=\\d*).+?c-price\\\\\">(\\d*)").matcher(bodyString);
                            while (matcher.find()) {
                                String spName = matcher.group(1) + matcher.group(2) + matcher.group(3);
                                Double spPrice = Double.valueOf(matcher.group(5));
                                String spUrl = matcher.group(4);
                                logger.debug(spName);
                                logger.debug(spPrice + "");
                                logger.debug(spUrl);
                                // 使用该商品的全称按销量搜索，分析第一页商品的信息，判断该商品是否可用
                                Boolean isUsable = compareSp(spName, spPrice);
                                if (isUsable) {
                                    // 获取此店铺的商品URL
                                    spUrlList_temp.add("https://" + spUrl);
                                } else {
                                    logger.info("该链接被舍弃 https://{}", spUrl);
                                    failNo_reality++;
                                    if (failNo_reality >= failNo) {
                                        isBreak = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    // 在最里层循环体close Response
                    if (response != null) {
                        response.close();
                    }
                }

                spUrlList.addAll(spUrlList_temp);// 将本店查询到的所有商品链接加入总商品链接集合

                logger.debug("店铺链接{} 店铺搜索关键词{} 价格范围{}-{} 实际搜索页数:{} 累计共找到{}个商品链接", twoUrl, twoKeyword, minPrice, maxPrice, allPageNo, spUrlList.size());
            }

            for (String spUrl : spUrlList) {
                logger.info(spUrl);
            }
            // ---END 页数for循环
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

    // 使用商品的全称按销量搜索，分析第一页商品的信息，判断该商品是否可用
    private Boolean compareSp(String spName, Double spPrice) throws Exception {
        CloseableHttpResponse response = null;
        try {
            String url = replaceThreeUrl(threeUrl, spName, spPrice * multiple);
            HttpGet httpGet = new HttpGet(url);
            buildHeaderByMap(httpGet, threeHeadDataMap);
            response = httpClient.execute(httpGet);
            HttpEntity responseEntity = response.getEntity();
            if (responseEntity != null) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    String bodyString = EntityUtils.toString(responseEntity);
                    bodyString = bodyString.replaceAll("\\s|\\n|\\t", "").replaceFirst("recommendAuctions[\\s\\S]*", "");

                    // 原本的逻辑，销量排序查出第一页，第一页中有3个价格在范围之内的就通过
                    /*Matcher matcher = Pattern.compile("raw_title\":\"(\\S+?)\"\\S+?view_price\":\"(\\d*)").matcher(bodyString);
                    int count = 0;
                    while (matcher.find()) {
                        String otherSpName = matcher.group(1);
                        Double otherSpPrice = Double.valueOf(matcher.group(2));
                        logger.info(otherSpName);
                        logger.info(otherSpPrice + "");
                        if (spName.equals(otherSpName)) {
                            if (otherSpPrice >= spPrice * multiple) {
                                count++;
                                if (count >= passNo) {
                                    break;
                                }
                            }
                        }
                    }*/

                    // 查询到的商品有3个就通过
                    Matcher matcher = Pattern.compile("raw_title").matcher(bodyString);
                    int count = 0;
                    while (matcher.find()) {
                        count++;
                        if (count >= passNo) {
                            break;
                        }
                    }
                    if (count >= passNo) {
                        return true;
                    } else {
                        return false;
                    }
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
        return false;
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

    // 根据三级搜索配置的条件，更换URL中的店铺URL及参数
    private String replaceThreeUrl(String url, String spName, Double spPrice_multiple) throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("q", URLDecoder.decode(spName, "gb2312"));
        map.put("sort", threeSort);
        map.put("filter", "reserve_price%5B" + spPrice_multiple + "%2C" + spPrice_multiple * 10 + "%5D");
        return addOrReplaceUrlParam(url, map);
    }

    // 将天猫店铺的淘宝URL转换为天猫URL（商品数据时从天猫URL中获取的）
    private String transformUrlTbToTm(String dpUrl) throws Exception {
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

    // 获取店铺类型(tm tb)、店铺名、旺旺名、店铺首页URL
    private List<Map<String, String>> getAllDpInfo(String data) {
        List<Map<String, String>> list = new ArrayList<>();
        data = data.replaceAll("\\s|\\n|\\t", "");
        Matcher matcher = Pattern.compile("\"title\":\"(\\S{1,28}?)\",\"nick\":\"(\\S{1,28}?)\"\\S+?shopUrl\":\"//(\\S+?)\"\\S+?\"shopIcon\":\\{(\\S+?)\\}").matcher(data);
        while (matcher.find()) {
            Map<String, String> map = new HashMap<String, String>();
            // 店铺名
            String dpName = matcher.group(1);
            logger.debug("店铺名{}", dpName);
            map.put("dpName", dpName);
            // 旺旺名
            String wwName = matcher.group(2);
            logger.debug("旺旺名{}", wwName);
            map.put("wwName", wwName);
            // 店铺首页URL
            String dpUrl = matcher.group(3);
            logger.debug("店铺页URL{}", dpUrl);
            map.put("dpUrl", "https://" + dpUrl);
            String dpType = matcher.group(4);
            if (dpType.contains("\"title\":\"天猫\"")) {
                map.put("dpType", "tm");
            } else {
                map.put("dpType", "tb");
            }
            logger.debug("店铺类型{}", dpType);

            // 若果是天猫店铺，发送请求获取该url对应的天猫店铺首页URL
            // 此步骤放在获取该店铺商品前执行，因为在此的店铺URL后续可能会被过滤掉
            list.add(map);
        }
        return list;
    }
}
