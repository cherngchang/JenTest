@GrabResolver(name='jenkins', root='http://repo.jenkins-ci.org/public/')
@Grapes([
  @Grab('org.yaml:snakeyaml:1.18'),
  @Grab('org.jenkins-ci.plugins:cloudbees-folder:6.14'),
  @Grab('org.jenkins-ci.plugins:credentials:2.3.12')
])
import org.yaml.snakeyaml.Yaml
import jenkins.model.*
import cloudbees.model.*
import com.cloudbees.hudson.plugins.folder.*
import hudson.model.Item
import hudson.model.Items

jenkins = Jenkins.instance

hudson.FilePath workspace = hudson.model.Executor.currentExecutor().getCurrentWorkspace()

def platform = new Yaml().load(readFileFromWorkspace("jenkins-jobs/pipe/platform.yaml"))
def CONF_FILE = 'none.yaml'
List appConfigs = workspace.list("jenkins-jobs/pipe/${CONF_FILE}")
out.println("Below is the appConfigs class")
out.println(appConfigs.getClass().getName())  //java.util.ArrayList

appConfigs.each { config ->
  def yaml = new Yaml().load(config.readToString())
  out.println("app config: $yaml")
  yaml['apps'].each { app ->
    if (app['cicd'] != true) reture //stop further operation
    app['gsi'] = yaml['gsi-code']
    // normalize platform name based on input
    switch(app['target-hosting-platform']) {
      case ~/.*openshift$/:
        app['target-hosting-platform'] = 'openshift'
        // for backward compatibility as we did not take input for namespace previously
        if (!app.containsKey('openshift')) {
          project = [
              cluster: "on-prem-dev",
              project: app['gsi'] + "-dev",
              environment: 'dev' 
          ]
          app['openshift'] = [ projects: [project] ]
          out.println("No openshift hash found in config - add default - ${app['openshift']}")
        }
        break;
      case ~/.*puppet$/:
        app['target-hosting-platform'] = 'puppet'
        break;
      case ~/.*none$/:
        app['target-hosting-platform'] = 'none'
        break;
    } 

    out.println("app['gsi'] = " + app['gsi'] )
    out.println("app['target-hosting-platform'] = " + app['target-hosting-platform'] )

    //normalize type of app based on input
    switch(app['type']) {
      case ~/^java.*/:
        app['type'] = 'java'
        break;
      case ~/^docker.*/:
        app['type'] = 'docker'
        break;
      case ~/^angular.*/:
        app['type'] = 'angular'
        break;
      case ~/^js.*/:
        app['type'] = 'js'
        break;
      case ~/^dotnetcore.*/:
        app['type'] = 'dotnetcore'
        break;
      case ~/^android.*/:
        app['type'] = 'android'
        break;
      case ~/^ios.*/:
        app['type'] = 'ios'
        break;
      case ~/^api-promotion.*/:
        app['type'] = 'api-promotion'
        break;  
    }
    app['branch'] = app['branch'] ?: 'master'

    def topLevelFolder = "${app['gsi']}-${app['bitbucket-project']}"
    def repoFolder = "${topLevelFolder}/${app['repo']}"

    //put the build job in the branch specific folder if branch is not master
    if(app['target-hosting-platform'] ==~ /.*puppet$/ && "${app['branch']}" != 'master') {
      branch = "${app['branch']}".replaceAll("[/]", "-") 
      repoFolder = "${topLevelFolder}/${app['repo']}_${branch}"
    }

    out.println("app['type'] = " + app['type'] )
    out.println("app['branch'] = " + app['branch'])
    out.println("topLevelFolder = " + topLevelFolder)
    out.println("repoFolder = " + repoFolder)

    //get the existing credentials if any
    String FOLDER_CREDENTIALS_PROPERTY_NAME = 'com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider$FolderCredentialsProperty' 
    Node folderCredentialsPropertyNode
    Item buildFolder = jenkins.getItem(topLevelFolder)
    if (buildFolder) {
      def folderCredentialsProperty = buildFolder.getProperties().getDynamic(FOLDER_CREDENTIALS_PROPERTY_NAME)  
      if (folderCredentialsProperty) {
        String xml = Items.XSTREAM2.toXML(folderCredentialsProperty)
        folderCredentialsPropertyNode = new XmlParser().parseText(xml)
      }
    }

    folder("${topLevelFolder}") {
      description("This top level folder is meant to separate pipeline hobs on a per appliation basis. Pipeline job should ot be added at this level")
      properties {
        envVarsFolderProperty {
          //properties("GSI=${app['gsi']}")
          properties("GSI=abc123")
        }  
      }
      configure { project ->
        def groups = project / 'properties' / 'com.cloudbees.hudson.plugins.folder.properties.FolderProxyGroupContainer'(plugin:'nectar-rbac@5.23') / groups { 'nectar.plugins.rbac.groups.Group' }
        def readGroup = [name('reader'), role(propagateToChildren:"false", 'read')]
        for (m in yaml['roles']['readers']) {
          readGroup << member("${m}")
        }
        groups.appendNode('nectar.plugins.rbac.groups.Group', readGroup)
        //reset the captured credentials
        if (folderCredentialsPropertyNode) {
          project / 'properties' << folderCredentialsPropertyNode
        }
      }
    }

    folder("${repoFolder}") {
      description("The pipeline jobs in this folder is owned and managed by the CI/CD platform team. Authorized users may execute the jobs but will not be able to edit them. Please contact the CI/CD platform team for assistance.")
      configure { project ->
        def groups = project / 'properties' / 'com.cloudbees.hudson.plugins.folder.properties.FolderProxyGroupContainer'(plugin:'nectar-rbac@5.23') / groups { 'nectar.plugins.rbac.groups.Group' }
        def readGroup = [name('reader'), role(propagateToChildren:"true", 'read')]
        for (m in yaml['roles']['readers']) {
          readGroup << member("${m}")
        }
        groups.appendNode('nectar.plugins.rbac.groups.Group', readGroup)
        // Build out the list of authorized cloud for this folder
        def clouds = project / 'properties' / 'org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty'(plugin:'kubernetes@1.13.7') / permittedClouds {}
        for (n in [platform['jenkins']['pipeline-namespaces']]) {
          for (item in n) {
            clouds.appendNode("string", item)
          }
        }
      }
    }

  }
}