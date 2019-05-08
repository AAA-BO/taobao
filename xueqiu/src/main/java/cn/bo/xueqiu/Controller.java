package cn.bo.xueqiu;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@EnableAutoConfiguration
public class Controller {
    @RequestMapping("/")
    String home() {
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;

        try {
            httpClient = HttpClientBuilder.create().build();//两个方法中不会同时使用该属性，所以可以定义在此
            HttpGet httpGet = new HttpGet("https://xueqiu.com/");
            response = httpClient.execute(httpGet);
            HttpEntity responseEntity = response.getEntity();
            if (responseEntity != null) {
                if (response.getStatusLine().getStatusCode() == 200) {
//                    System.out.println(EntityUtils.toString(responseEntity));
                    return EntityUtils.toString(responseEntity);
                    // 使用正则解析
//                    Pattern pattern = Pattern.compile("(shopUrl.*?(shop)?)(shop.*?com)");
//                    Matcher matcher = pattern.matcher(EntityUtils.toString(responseEntity).replaceAll("\\s|\\n|\\t", ""));
//                    while (matcher.find()) {
//                        System.out.println("https://" + matcher.group(3));
//                    }
                }
            }

            if (response != null) {
                response.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        return "Hello World!";
    }

    @RequestMapping("/snowman/provider/geetest")
    @ResponseBody
    String test() {
        return "\n" +
                "{\"success\":1,\"new_captcha\":true,\"available\":1,\"challenge\":\"2469d9d81602cfa2f471826dbba85b54\",\"gt\":\"b68f7c51ff1ee91cad5ece5b8a1c9a56\"}";

    }

}