package com.textureflow.intelligence.api;

/** What kind of data a job may carry. Helpers never see PRIVATE* unless their trust tier allows it. */
public enum DataClass {
    PRIVATE,
    PRIVATE_DERIVED,
    PUBLIC
}
