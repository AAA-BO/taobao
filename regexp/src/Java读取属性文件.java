import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

public class Java读取属性文件 {

    public static void main(String[] args) throws Exception {
        InputStream inStream = ClassLoader.getSystemResourceAsStream("aaa.properties");  ;
        Properties prop = new Properties();
        prop.load(inStream);        Enumeration<Object> keys = prop.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            System.out.println(key.toString()+"="+prop.getProperty(key.toString()));
        }
    }
}
