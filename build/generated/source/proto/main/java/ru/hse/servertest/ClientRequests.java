// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: client_requests.proto

package ru.hse.servertest;

public final class ClientRequests {
  private ClientRequests() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_ru_hse_servertest_ArrayToSort_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_ru_hse_servertest_ArrayToSort_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\025client_requests.proto\022\021ru.hse.serverte" +
      "st\"\034\n\013ArrayToSort\022\r\n\005array\030\001 \003(\005B\002P\001b\006pr" +
      "oto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_ru_hse_servertest_ArrayToSort_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_ru_hse_servertest_ArrayToSort_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_ru_hse_servertest_ArrayToSort_descriptor,
        new java.lang.String[] { "Array", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
