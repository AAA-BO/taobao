import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexpTest {
    public static void main(String[] args) {
        String line = "";
        line = line.replaceAll("\\s|\\n|\\t", "");
        Pattern r = Pattern.compile("(item-name.*?(item)?)(item.taobao.com/item.htm\\?id=\\d*)");
        Matcher m = r.matcher(line);
        int j = 0;
        while (m.find( )) {
            System.out.println(m.group(3));
            j++;
        }

    }
}
