package utilities

class ScriptLoader {

    static load(String path, String script) {
        def shell = new GroovyShell(this.class.classLoader, new Binding())
        shell.getClassLoader().addURL(new File(path).toURL())
        return shell.evaluate("new " + script + "()")
    }

}
