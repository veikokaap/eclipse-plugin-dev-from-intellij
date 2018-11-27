# IntelliJ IDEA plugin for aiding development of Eclipse IDE plugins

This plugin was created to simplify importing an Eclipse IDE plugin maven tycho project to IntelliJ.

It's still very much work-in-progress, but currently it offers the following features:
 * Add Eclipse IDE **run configuration** (allows selecting eclipse installation, workspace and plugins from the maven tycho project to execute on the IDE)
 * Switch every imported tycho packaging maven project to use **Eclipse compiler**
 * Try to find actual project modules for OSGi dependencies in MANIFEST.MF
   * By default, it's possible that you have 2 eclipse-plugins and one is the required bundle of the other, but IntelliJ takes the dependency from .m2 repository instead of just taking the sibling module
