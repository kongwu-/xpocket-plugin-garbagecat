

import org.eclipselabs.garbagecat.GarbageCat;


public class GarbageCatTest {
    public static void main(String[] args) {
        String reportBody = GarbageCat.analyze(new String[]{"-t","99","C:\\Users\\jiang\\IdeaProjects\\xpocket-plugin-garbagecat\\src\\test\\resources\\gc-example.log"});
        System.out.println(reportBody);
    }
}
