package com.github.kongwu.xpocket.plugin.garbagecat;

import com.perfma.xlab.xpocket.spi.XPocketPlugin;
import com.perfma.xlab.xpocket.spi.command.AbstractXPocketCommand;
import com.perfma.xlab.xpocket.spi.command.CommandInfo;
import com.perfma.xlab.xpocket.spi.process.XPocketProcess;
import org.eclipselabs.garbagecat.GarbageCat;

/**
 * 用于实现每个命令的核心逻辑，一个或者多个命令指向一个类。
 *
 * @author kongwu <jiangxin1035@163.com>
 */

@CommandInfo(name = "analyze", usage = "analyze <options> <gclogfile>", index = 0)
@CommandInfo(name = "help", usage = "help, show help information", index = 1)
public class GarbageCatXPocketCommand extends AbstractXPocketCommand {

    @Override
    public void init(XPocketPlugin plugin) {
        super.init(plugin);
    }

    @Override
    public void invoke(XPocketProcess process) {
        String cmd = process.getCmd();
        if("help".equals(cmd)){
            process.output(GarbageCat.analyze(new String[]{"-h"}));
        }else{
            process.output("Analyzing...please wait");
            process.output(GarbageCat.analyze(process.getArgs()));
        }
        process.end();
    }

}
