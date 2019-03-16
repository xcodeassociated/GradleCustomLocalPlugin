package com.peterabeles.gversion

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException

import java.text.DateFormat
import java.text.SimpleDateFormat

enum Language {
    JAVA,
    KOTLIN;

    static Language convert( String name ) {
        switch( name.toUpperCase() ) {
            case "JAVA": return JAVA
            case "KOTLIN": return KOTLIN
        }
        throw new IllegalArgumentException("Unknown language. "+name)
    }
}

class GVersionExtension {
    String srcDir
    String classPackage = ""
    String className = "GVersion"
    String dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    String timeZone = "UTC"
    String language = "Java"
    // If the language allows implicit types should it explicitly state the types?
    boolean explicitType = false
    boolean debug = false
}

public class GVersion implements Plugin<Project> {
    void foo() {
        println "dupa"
    }

    GVersionExtension extension

    static String gversion_file_path(Project project, GVersionExtension extension ) {
        if(extension.srcDir == null )
            throw new RuntimeException("Must set gversion_file_path")

        String relative_path

        if( !new File(extension.srcDir).isAbsolute() ) {
            if(extension.debug) println("path relative to sub-project")
            // relative to sub-project
            relative_path = project.file(".").path
        } else {
            if(extension.debug) println("absolute path")
            // relative to system
            relative_path = ""
        }

        File gversion_file_path = new File(extension.srcDir,
                extension.classPackage.replace(".",File.separator))
        return new File(relative_path,gversion_file_path.path).getPath()
    }

    String executeGetOutput( String command , String DEFAULT ) {
        def proc = command.execute(null,new File(System.getProperty("user.dir")))
        try {
            proc.consumeProcessErrorStream(new StringBuffer())
            proc.waitForOrKill(5000)
            if( proc.exitValue() != 0 ) {
                if( extension.debug ) {
                    System.err.println("command returned non-zero value: "+proc.exitValue())
                    System.err.println("pwd     = "+new File(".").getAbsolutePath())
                    System.err.println("command = "+command)
                    System.err.println("output  = "+proc.text.trim())
                }
                return DEFAULT
            }
            return proc.text.trim()
        } catch (IOException e) {
            if( extension.debug ) {
                e.printStackTrace(System.err)
            }
            return DEFAULT
        } finally {
            proc.closeStreams()
        }
    }

    static int[] parseGitVersion( String text ) {
        int []version = new int[3]

        if( text == null )
            return version
        String []words = text.split("\\s+")
        if( words.length != 3 )
            return version

        words = words[2].split("\\.")
        if( words.length != 3 )
            return version

        version[0] = Integer.parseInt(words[0])
        version[1] = Integer.parseInt(words[1])
        version[2] = Integer.parseInt(words[2])

        return version
    }

    void apply(Project project) {
        // Add the 'greeting' extension object
        extension = project.extensions.create('gversion', GVersionExtension)

        project.ext.checkProjectExistsAddToList = { whichProject , list ->
            try {
                project.project(whichProject)
                list.add(whichProject)
            } catch( UnknownProjectException ignore ) {}
        }

        // Force the release build to fail if it depends on a SNAPSHOT
        project.tasks.create('checkDependsOnSNAPSHOT'){
            doLast {
                if (project.version.endsWith("SNAPSHOT"))
                    return

                project.configurations.compile.each {
                    if (it.toString().contains("SNAPSHOT"))
                        throw new Exception("Release build contains snapshot dependencies: " + it)
                }
            }
        }

        project.task('checkForVersionFile') {
            doLast {
                def f = new File(gversion_file_path(project,extension),extension.className+".java")
                if( !f.exists() ) {
                    throw new RuntimeException("GVersion.java does not exist. Call 'createVersionFile'")
                }
            }
        }

        // Creates a resource file containing build information
        project.task('createVersionFile'){
            doLast {
                // For some strange reasons it was using the daemon's home directory instead of the project's!
                System.setProperty("user.dir", project.projectDir.toString())

                if (extension.debug) {
                    println "gversion.debug     = true"
                    println "project.name       " + project.name
                    println "rootProject path   " + project.rootProject.file(".").path
                    println "sub-project path   " + project.file(".").path
                }

                def gversion_file_path = gversion_file_path(project, extension)
                println("createVersionFile called. Path " + gversion_file_path)

                // Make sure the output directory exists. If not create it
                if (!new File(gversion_file_path).exists()) {
                    if (!new File(gversion_file_path).mkdirs()) {
                        throw new RuntimeException("Failed to make path: " + gversion_file_path)
                    } else {
                        println("Path did not exist. Created " + gversion_file_path)
                    }
                }

                def tz = TimeZone.getTimeZone(extension.timeZone)
                def version_of_git = parseGitVersion(executeGetOutput('git version', "UNKNOWN"))

                def git_revision = executeGetOutput('git rev-list --count HEAD', "-1")
                def git_sha = executeGetOutput('git rev-parse HEAD', "UNKNOWN")
                def git_tag = executeGetOutput('git --git-dir .git describe', "UNKNOWN")

                def git_date
                def date_format

                if (version_of_git[0] == 1) {
                    date_format = "yyyy-MM-dd HH:mm:ss Z"
                    git_date = executeGetOutput('git show -s --format=%ci HEAD', "UNKNOWN")
                } else {
                    date_format = "yyyy-MM-dd'T'HH:mm:ssXXX"
                    git_date = executeGetOutput('git show -s --format=%cI HEAD', "UNKNOWN")
                }
                if (!git_date.equals("UNKNOWN")) {
                    try {
                        // Let's convert this from local to the desired time zone and adjust the date format
                        DateFormat formatter = new SimpleDateFormat(date_format)
                        Date java_data = formatter.parse(git_date)
                        formatter = new SimpleDateFormat(extension.dateFormat)
                        formatter.setTimeZone(tz)
                        git_date = formatter.format(java_data)
                    } catch (RuntimeException e) {
                        if (extension.debug) {
                            e.printStackTrace(System.err)
                        }

                        git_date = "UNKNOWN"
                    }
                }

                def unix_time = System.currentTimeMillis()
                def formatter = new SimpleDateFormat(extension.dateFormat)
                formatter.setTimeZone(tz)
                String date_string = formatter.format(new Date(unix_time))

                Language language = Language.convert(extension.language)

                if (language == Language.JAVA) {
                    def f = new File(gversion_file_path, extension.className + ".java")

                    def writer = new FileWriter(f)
                    if (extension.classPackage.size() > 0) {
                        writer << "package $extension.classPackage;\n"
                        writer << "\n"
                    }
                    writer << "/**\n"
                    writer << " * Automatically generated file containing build version information.\n"
                    writer << " */\n"
                    writer << "public class " + extension.className + " {\n"
                    writer << "\tpublic static final String MAVEN_GROUP2 = \"$project.group\";\n"
                    writer << "\tpublic static final String MAVEN_NAME = \"$project.name\";\n"
                    writer << "\tpublic static final String VERSION = \"$project.version\";\n"
                    writer << "\tpublic static final int GIT_REVISION = $git_revision;\n"
                    writer << "\tpublic static final String GIT_TAG = \"$git_tag\";\n"
                    writer << "\tpublic static final String GIT_SHA = \"$git_sha\";\n"
                    writer << "\tpublic static final String GIT_DATE = \"$git_date\";\n"
                    writer << "\tpublic static final String BUILD_DATE = \"$date_string\";\n"
                    writer << "\tpublic static final long BUILD_UNIX_TIME = " + unix_time + "L;\n"
                    writer << "}"
                    writer.flush()
                    writer.close()
                } else if( language == Language.KOTLIN ) {
                    def f = new File(gversion_file_path, extension.className + ".kt")
                    def writer = new FileWriter(f)
                    if (extension.classPackage.size() > 0) {
                        writer << "package $extension.classPackage\n"
                        writer << "\n"
                    }

                    def typeString = extension.explicitType ? ": String " : ""
                    def typeInt = extension.explicitType ? ": Int " : ""
                    def typeLong = extension.explicitType ? ": Long " : ""

                    writer << "/**\n"
                    writer << " * Automatically generated file containing build version information.\n"
                    writer << " */\n"
                    writer << "const val MAVEN_GROUP $typeString= \"$project.group\"\n"
                    writer << "const val MAVEN_NAME $typeString= \"$project.name\"\n"
                    writer << "const val VERSION $typeString= \"$project.version\"\n"
                    writer << "const val GIT_REVISION $typeInt= $git_revision\n"
                    writer << "const val GIT_TAG $typeString= $git_revision\n"
                    writer << "const val GIT_SHA $typeString= \"$git_sha\"\n"
                    writer << "const val GIT_DATE $typeString= \"$git_date\"\n"
                    writer << "const val BUILD_DATE $typeString= \"$date_string\"\n"
                    writer << "const val BUILD_UNIX_TIME $typeLong= " + unix_time + "L\n"
                    writer.flush()
                    writer.close()
                } else {
                    throw new RuntimeException("BUG! Unknown language "+language)
                }
            }
        }

    }
}