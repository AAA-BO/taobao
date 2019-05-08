import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Stream {
    public static void main(String[] args) throws IOException {
        InputStream in = ClassLoader.getSystemResourceAsStream("dianpu.pro");  ;
        System.out.println(new BufferedReader(new InputStreamReader(in)).readLine());


    }
}
