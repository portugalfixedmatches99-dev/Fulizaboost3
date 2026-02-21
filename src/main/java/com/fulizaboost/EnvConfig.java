package com.fulizaboost;


import io.github.cdimascio.dotenv.Dotenv;

public class EnvConfig {
    public static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
}
