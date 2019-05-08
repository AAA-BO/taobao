import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class URL编码解码 {
    public static void main(String[] args) throws Exception {
        String s1 = "衬衫";
        String s2 = "%B3%C4%C9%C0";

        // 编码
        System.out.println(URLEncoder.encode(s1,"GB2312"));
        // 解码
        System.out.println(URLDecoder.decode(s2,"GB2312"));
    }
}
