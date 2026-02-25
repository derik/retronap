package com.deriklima.retronap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(AppRuntimeHints.class)
public class NativeImageConfiguration {}
