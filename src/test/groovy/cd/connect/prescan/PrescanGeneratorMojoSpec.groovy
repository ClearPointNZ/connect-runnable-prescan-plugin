package cd.connect.prescan

import com.bluetrainsoftware.classpathscanner.ClasspathScanner
import spock.lang.Specification

class PrescanGeneratorMojoSpec extends Specification{

	void "We scan for our artifacts"(){
		given: "we have a scanner and a classloader"
			ClasspathScanner scanner = new ClasspathScanner()
			ClassLoader loader = getClass().getClassLoader()
			PrescanGeneratorMojo mojo = new PrescanGeneratorMojo()
			mojo.targetDir = new File( getClass().getResource('/' ).getFile() )

		when: "we perform a scan"
			List<String> found = mojo.scan( scanner, loader )

		then: "we expect to find stuff"
			found.find { 'webxml=file:/WEB-INF/web.xml' == it }
			found.find { 'resource=file:/META-INF/resources/' == it }
			!found.find { 'resource=file:/META-INF/resources/empty.txt' == it }
			found.find { 'fragment=file:/META-INF/web-fragment.xml' == it }
			found.find { 'webxml=file:/META-INF/resources/WEB-INF/web.xml' == it }
	}

	void "We scan four our artifacts"(){
		given: "we have a scanner and a classloader"
			ClasspathScanner scanner = new ClasspathScanner()
			ClassLoader loader = new URLClassLoader( asURLArray('/jars/binks.jar', '/jars/fragment.jar' ) )
			PrescanGeneratorMojo mojo = new PrescanGeneratorMojo()
			mojo.targetDir = new File( getClass().getResource('/' ).getFile() )

		when: "we perform a scan"
			List<String> found = mojo.scan( scanner, loader )

		then: "we expect to find stuff"
			found.size() == 2
			found.find { 'webxml=jar:file:/jars/binks.jar!/WEB-INF/web.xml' == it }
			found.find { 'fragment=jar:file:/jars/fragment.jar!/META-INF/web-fragment.xml' == it }
	}

	private URL[] asURLArray( String... strings ) {
		return strings.collect { getClass().getResource( it ).toURI().toURL() }.toArray()
	}

}
