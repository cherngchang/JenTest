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



  }
}