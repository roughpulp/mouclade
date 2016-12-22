package rpulp.mouclade;


import com.google.common.collect.Lists;

import java.util.ArrayList;


class Config {

    private static String getEnvOrFail(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalArgumentException("missing env " + name);
        }
        return value;
    }

    private static final String EXE;
    private static final String IOS_LOG;
    static {
        EXE = getEnvOrFail("MOUCLADE_EXE");
        IOS_LOG = getEnvOrFail("MOUCLADE_IOS_LOG");

    }

    static final String PATH = getEnvOrFail("MOUCLADE_PATH");
    static final ArrayList<String> NATIVE_COMMAND = Lists.newArrayList(EXE, "--log-ios", IOS_LOG);
}
