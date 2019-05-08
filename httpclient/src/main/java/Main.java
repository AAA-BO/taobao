import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    // 公用属性
    private CloseableHttpClient httpClient = HttpClientBuilder.create().build();//两个方法中不会同时使用该属性，所以可以定义在此

    // 一级搜索
    private final String oneKeyword = "森马";//一级搜索关键词
    private final String liexing = "4";//店铺类型 所有(1) 天猫(2) 淘宝(3) 淘宝分级(4)
    private final String jibie = "jin";//淘宝店级别 金冠(jin) 皇冠(huang) 钻级(zhuan) 心级(xin)
    private final Integer onePageNumber = 2;//一级搜索页数
    private Collection<String> dpUrlList = new ArrayList<>();//一级搜索获取到的所有店铺的首页链接
    private String oneFileName = "one.pro";
    private HashMap<String, String> oneFileDataMap = new HashMap<>();
    private String oneUrl = null;//一级搜索请求URL


    // 二级搜索
    private final String twokeyword = "";//二级搜索关键词
    private Collection<String> spUrlList = new ArrayList<>();//二级搜索获取到的该店铺中的所有商品的链接
    private final Integer twoPageNumber = 10;// 二级搜索页数
    private Integer minPrice = 0;
    private Integer maxPrice = 999999;
    private String twoFileName = "two.pro";
    private HashMap<String, String> twoFileDataMap = new HashMap<>();


    public Main() throws Exception {
        BufferedReader bfReader = null;
        try {
            // 加载配置文件数据到Map
            String[] ss = new String[2];
            ss[0] = oneFileName;
            ss[1] = twoFileName;

            Map[] maps = new Map[2];
            maps[0] = oneFileDataMap;
            maps[1] = twoFileDataMap;

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
                        if (ss[i].equals(oneFileName)) {
                            // 根据一级搜索配置的条件，更换URL中的参数
                            oneUrl = replaceOneUrl(url);
                        } else if (ss[i].equals(twoFileName)) {
                            // 根据二级搜索配置的条件，更换URL中的参数
                            // TODO
                        }
                        firrstLineflag = false;
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
        new Main().getDpUrl();
    }


    /**
     * 获取店铺的链接
     */
    private void getDpUrl() throws Exception {
        CloseableHttpResponse response = null;

        try {
            for (int i = 0; i < onePageNumber; i++) {
                HttpGet httpGet = new HttpGet(oneUrl + "&s=" + i * 20);// 加上分页

                // 设置请求头
                buildHeaderByMap(httpGet, oneFileDataMap);

                response = httpClient.execute(httpGet);
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    System.out.println(response.getStatusLine().getStatusCode());
                    if (response.getStatusLine().getStatusCode() == 200) {
                        // 使用正则解析
                        Pattern pattern = Pattern.compile("(shopUrl.*?(shop)?)(shop.*?com)");
                        String string = EntityUtils.toString(responseEntity);
//                        System.out.println(string);
                        Matcher matcher = pattern.matcher(string.replaceAll("\\s|\\n|\\t", ""));
                        while (matcher.find()) {
                            System.out.println("https://" + matcher.group(3));
                            // 获取此店铺的商品
                            dpUrlList.add("https://" + matcher.group(3));
                        }
                    }
                }

                if (response != null) {
                    response.close();
                }
            }
            // ---END 页数for循环
            System.out.printf("log:\t\t店铺搜索关键词:%s\t店铺级别:%s\t搜索页数:%d\t共找到:%d个店铺链接\n\n\n", oneKeyword, jibie, onePageNumber, dpUrlList.size());
            //getSpUrl();
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
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
    private void getSpUrl() throws Exception {
        CloseableHttpResponse response = null;

        try {
            // 遍历所有店铺
            for (String dpUrl : dpUrlList) {
                // 根据商品页数循环获取每页商品
                int allPage = 1;
                for (int nowPage = 1; nowPage <= allPage; nowPage++) {
                    HttpGet httpGet = new HttpGet(dpUrl + "/i/asynSearch.htm?_ksTS=1556185461530_180&callback=jsonp181&input_charset=gbk&mid=w-14162527726-0&wid=14162527726&path=/search.htm&search=y&spm=a1z10.3-c.w4002-14162527726.87.51ba560aARsTDT&viewType=grid&lowPrice=" + minPrice + "&highPrice=" + maxPrice + "&keyword=" + URLEncoder.encode(twokeyword, "GB2312") + "&pageNo=" + nowPage);

                    // 设置请求头信息
                    buildHeaderByMap(httpGet, twoFileDataMap);

                    response = httpClient.execute(httpGet);
                    HttpEntity responseEntity = response.getEntity();
                    if (responseEntity != null) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            String responseBody = EntityUtils.toString(responseEntity).replaceAll("\\s|\\n|\\t", "");
                            // 获取第一页数据时，找下总页数
                            if (nowPage == 1) {
                                Matcher matcher = Pattern.compile("(page-info.*?/)(\\d)").matcher(responseBody);
                                if (matcher.find()) {
                                    allPage = Integer.valueOf(matcher.group(2));
                                }
                            }

                            Matcher matcher = Pattern.compile("(item-name.*?(item)?)(item.taobao.com/item.htm\\?id=\\d*)").matcher(responseBody);
                            while (matcher.find()) {
                                System.out.println("https://" + matcher.group(3));
                                // 获取此店铺的商品
                                spUrlList.add("https://" + matcher.group(3));
                            }
                        }
                    }

                    // 在最里层循环体close Response
                    if (response != null) {
                        response.close();
                    }
                }
                System.out.printf("log:\t\t店铺链接:%s\t店铺搜索关键词:%s\t价格范围:%d-%d\t店铺商品页数:%d\t累计共找到:%d个商品链接\n\n", dpUrl, twokeyword, minPrice, maxPrice, allPage, spUrlList.size());

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
        url = url.replaceFirst("q=.*?(&|$)", "q=" + URLEncoder.encode(oneKeyword, "utf-8") + "&");//关键词
        switch (liexing) { //店铺类型 所有(1) 天猫(2) 淘宝(3) 淘宝分级(4)
            case "1":
                url = url.replaceFirst("data-key=.*?(&|$)", "data-key=shop_type%2Cisb%2Cratesum&");
                url = url.replaceFirst("data-value=.*?(&|$)", "data-value=%2C%2C&");
                url = url.replaceFirst("isb=.*?(&|$)", "isb=&");
                url = url.replaceFirst("ratesum=.*?(&|$)", "ratesum=&");
                break;
            case "2":
                url = url.replaceFirst("data-key=.*?(&|$)", "data-key=isb%2Cshop_type%2Cratesum%2Cgoodrate&");
                url = url.replaceFirst("data-value=.*?(&|$)", "data-value=1%2C%2C%2C&");
                url = url.replaceFirst("isb=.*?(&|$)", "isb=1&");
                url = url.replaceFirst("ratesum=.*?(&|$)", "ratesum=&");
                break;
            case "3":
                url = url.replaceFirst("data-key=.*?(&|$)", "data-key=isb%2Cshop_type%2Cratesum&");
                url = url.replaceFirst("data-value=.*?(&|$)", "data-value=0%2C%2C&");
                url = url.replaceFirst("isb=.*?(&|$)", "isb=0&");
                url = url.replaceFirst("ratesum=.*?(&|$)", "ratesum=&");
                break;
            case "4":
                url = url.replaceFirst("data-key=.*?(&|$)", "data-key=isb%2Cshop_type%2Cratesum&");
                url = url.replaceFirst("data-value=.*?(&|$)", "data-value=0%2C%2C" + jibie+"&"); // 店铺级别
                url = url.replaceFirst("isb=.*?(&|$)", "isb=0&");
                url = url.replaceFirst("ratesum=.*?(&|$)", "ratesum=" + jibie + "&");
                break;
        }
        return url;
    }
}
