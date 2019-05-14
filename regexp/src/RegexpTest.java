import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexpTest {
    public static void main(String[] args) {
        String line = "1234";
//        line = line.replaceAll("\\s|\\n|\\t", "");
        Pattern r = Pattern.compile("(1(2))|(3(4))");
        Matcher m = r.matcher(line);
        while (m.find( )) {
            System.out.println(m.group(1));
            System.out.println(m.group(2));
            System.out.println(m.group(3));
            System.out.println(m.group(4));
        }

    }
}
