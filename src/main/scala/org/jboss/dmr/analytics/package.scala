package org.jboss.dmr

package object analytics {

  /** Taken from [org.jboss.as.controller.registry.AttributeAccess.AccessType] */
  object AccessType extends Enumeration {
    type AccessType = Value
    val UNKNOWN = Value("unknown")
    val READ_ONLY = Value("read-only")
    val READ_WRITE = Value("read-write")
    val METRIC = Value("metric")
  }

  /** Taken from [org.jboss.as.controller.registry.AttributeAccess.Flag] */
  object RestartPolicy extends Enumeration {
    type RestartPolicy = Value
    val UNKNOWN = Value("unknown")
    val RESTART_NONE = Value("no-services")
    val RESTART_JVM = Value("jvm")
    val RESTART_ALL_SERVICES = Value("all-services")
    val RESTART_RESOURCE_SERVICES = Value("resource-services")
  }

  /** Taken from [org.jboss.as.controller.registry.AttributeAccess.Flag] */
  object Storage extends Enumeration {
    type Storage = Value
    val UNKNOWN = Value("unknown")
    val CONFIGURATION = Value("configuration")
    val RUNTIME = Value("runtime")
  }
}
