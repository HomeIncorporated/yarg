package com.haulmont.newreport.formatters.impl.doc.connector;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LinuxProcessManager extends JavaProcessManager implements ProcessManager {
    private static final Pattern PS_OUTPUT_LINE = Pattern.compile("^\\s*(\\d+)\\s+(.*)$");

    protected String[] psCommand() {
        return new String[]{"/bin/ps", "-e", "-o", "pid,args"};
    }

    @Override
    public List<Long> findPid(String host, int port) {
        try {
            String regex = Pattern.quote(host) + ".*" + port;
            Pattern commandPattern = Pattern.compile(regex);
            List<String> lines = execute(psCommand());
            List<Long> result = new ArrayList<Long>();

            for (String line : lines) {
                Matcher lineMatcher = PS_OUTPUT_LINE.matcher(line);
                if (lineMatcher.matches()) {
                    String command = lineMatcher.group(2);
                    Matcher commandMatcher = commandPattern.matcher(command);
                    if (commandMatcher.find()) {
                        result.add(Long.parseLong(lineMatcher.group(1)));
                    }
                }
            }

            return result;
        } catch (IOException e) {
            log.error("An error occured while searching for soffice PID in linux system", e);
        }

        return Collections.singletonList(PID_UNKNOWN);
    }

    public void kill(Process process, List<Long> pids) {
        log.info("Linux office process manager is going to kill following processes " + pids);
        for (Long pid : pids) {
            try {
                if (pid > 0) {
                    execute("/bin/kill", "-KILL", Long.toString(pid));
                } else {
                    log.warn("Fail to kill open office process with platform dependend manager - PID not found.");
                    super.kill(process, Collections.singletonList(pid));
                }
            } catch (Exception e) {
                log.error(String.format("An error occured while killing process %d in linux system. Process.destroy() will be called.", pid), e);
                super.kill(process, Collections.singletonList(pid));
            }
        }
    }

    private List<String> execute(String... args) throws IOException {
        Process process = new ProcessBuilder(args).start();
        @SuppressWarnings("unchecked")
        List<String> lines = IOUtils.readLines(process.getInputStream());
        return lines;
    }

}