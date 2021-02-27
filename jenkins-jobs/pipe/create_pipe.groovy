@GrabResolver(name='jenkins', root='http://repo.jenkins-ci.org/public/')
@Grapes([
  @Grap('org.yaml:snakeyaml:1.18'),
  @Grap('org.jenkins-ci.plugins:cloudbees-folder:6.14'),
  @Grap('org.jenkins-ci.plugins:credentials:2.3.12')
])
import org.yaml.snakeyaml.Yaml
import jenkins.model.*
import cloudbees.model.*
import com.cloudbees.hudson.plugins.folder.*
import hudson.model.Item
import hudson.model.Items

jenkins = Jenkins.instance

hudson.FilePtah workspace = hudson.model.Executor.currentExecutor.getCurrentWorkspace()

def platform = new Yaml().load(readFileFromWorkspace("jenkins-jobs/pipe/platform.yaml"))
def CONF_FILE = 'none.yaml'
List appConfigs = workspace.list("jenkins-jobs/pipe/${CONF_FILE}")
out.println("Below is the appConfigs class")
out.println(appConfigs.getClass().getName())
