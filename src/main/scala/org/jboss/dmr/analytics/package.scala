package org.jboss.dmr

package object analytics {

  /** Taken from [org.jboss.as.controller.registry.AttributeAccess.AccessType] */
  object AccessType extends Enumeration {
    type AccessType = Value
    val NA = Value("n/a")
    val READ_ONLY = Value("read-only")
    val READ_WRITE = Value("read-write")
    val METRIC = Value("metric")
  }

  /** Taken from [org.jboss.as.controller.registry.AttributeAccess.Flag] */
  object RestartPolicy extends Enumeration {
    type RestartPolicy = Value
    val NA = Value("n/a")
    val RESTART_NONE = Value("no-services")
    val RESTART_JVM = Value("jvm")
    val RESTART_ALL_SERVICES = Value("all-services")
    val RESTART_RESOURCE_SERVICES = Value("resource-services")
  }

  /** Taken from [org.jboss.as.controller.registry.AttributeAccess.Flag] */
  object Storage extends Enumeration {
    type Storage = Value
    val NA = Value("n/a")
    val CONFIGURATION = Value("configuration")
    val RUNTIME = Value("runtime")
  }
}
