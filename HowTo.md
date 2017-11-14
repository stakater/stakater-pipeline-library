# HowTo

How to extend Jenkins with Shared Libraries?

With Shared Libraries Jenkins lets you share common code on pipelines across different repositories of your organization. 

- [Jenkins Extending with Shared Libraries](https://jenkins.io/doc/book/pipeline/shared-libraries/)
- [Extending your Pipeline with Shared Libraries, Global Functions and External Code ](https://jenkins.io/blog/2017/06/27/speaker-blog-SAS-jenkins-world/#extending-your-pipeline-with-shared-libraries-global-functions-a)

## Directory structure

The directory structure of a Shared Library repository is as follows:

- The `src` directory should look like standard Java source directory structure. This directory is added to the classpath when executing Pipelines.

- The `vars` directory hosts scripts that define global variables accessible from Pipeline. The basename of each *.groovy file should be a Groovy (~ Java) identifier, conventionally camelCased. The matching *.txt, if present, can contain documentation, processed through the system’s configured markup formatter (so may really be HTML, Markdown, etc., though the txt extension is required).

The Groovy source files in these directories get the same “CPS transformation” as in Scripted Pipeline.

- A `resources` directory allows the libraryResource step to be used from an external library to load associated non-Groovy files. Currently this feature is not supported for internal libraries.

Other directories under the root are reserved for future enhancements.

## How to test?

If you use Jenkins as your CI workhorse and you enjoy writing pipeline-as-code, you already know that pipeline code is very powerful but can get pretty complex.

https://github.com/jenkinsci/JenkinsPipelineUnit

