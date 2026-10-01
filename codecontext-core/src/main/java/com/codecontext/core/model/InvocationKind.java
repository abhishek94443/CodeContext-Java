package com.codecontext.core.model;

/**
 * Classification of code invocations and type references.
 */
public enum InvocationKind {
    METHOD_CALL,
    CONSTRUCTOR_CALL,
    STATIC_METHOD_CALL,
    TYPE_REFERENCE,
    SUPER_CALL
}