package grails.plugins.directoryservice

import grails.test.mixin.*
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.junit.*

import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.Filter as LDAPFilter
import com.unboundid.ldap.sdk.SearchResultEntry

import grails.plugins.directoryservice.listener.InMemoryDirectoryServer

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
class DirectoryServiceTests extends GroovyTestCase {

    /* DirectoryService to use for all tests. */
    def directoryService
    
    /* Used for comparison. */
    def peopleBaseDn = 'ou=people,dc=someu,dc=edu'
    
    /* Used for testing the fake AD server. */
    def accountsBaseDn = 'ou=accounts,dc=someu,dc=edu'
    
    /* Used for testing the French person branch. */
    def accountsEconomicsBaseDn = 'ou=Economics,ou=accounts,dc=someu,dc=edu'
    
    def dirInMemServer
    def adInMemServer
    
    /**
     * Set up by creating an DirectoryService and setting grailsApplication config
     * based on ConfigurationHolder.config. Then set up the UnboundID
     * InMemoryServer.
     */
    protected void setUp() {
        super.setUp()
        
        def dirConfig = ConfigurationHolder.config.ds.sources['directory']
        def adConfig = ConfigurationHolder.config.ds.sources['ad']

        dirInMemServer = new InMemoryDirectoryServer(
            "dc=someu,dc=edu",
            dirConfig,
            "test/ldif/schema/directory-schema.ldif",
            "test/ldif/directory.ldif"
        )
        
        adInMemServer = new InMemoryDirectoryServer(
            "dc=someu,dc=edu",
            adConfig,
            "test/ldif/schema/ad-schema.ldif",
            "test/ldif/accounts.ldif"
        )
        
        //def inMemServer = new InMemoryServer()
        //def conn = inMemServer.connection()
        //directoryService.setPorts("${inMemServer.listenPort()}")
    }
    
    protected void tearDown() {
        dirInMemServer?.shutDown()
        adInMemServer?.shutDown()
    }
    
    /*
     * Test the getting the source from the provided base.
     */
    void testSource() {
        def source = directoryService.sourceFromBase(peopleBaseDn)
        
        assertEquals source.address, 'localhost'
        assertEquals source.port, '33389'
        assertFalse  source.useSSL
        assertTrue   source.trustSSLCert
        assertEquals source.bindDN, 'cn=Directory Manager'
        assertEquals source.bindPassword, 'password'
        
        source = directoryService.sourceFromBase(accountsBaseDn)
        assertEquals source.address, 'localhost'
        assertEquals source.port, '33268'
        assertFalse  source.useSSL
        assertTrue   source.trustSSLCert
        assertEquals source.bindDN, 'cn=AD Manager'
        assertEquals source.bindPassword, 'password'
    }
    
    /**
     * Test andFilterFromArgs to make sure it outputs an LDAPFilter.
     */
    void testAndFilterFromArgs() {
        def args = ['sn':'nguyen']
        def filter = directoryService.andFilterFromArgs(args)
        assertNotNull filter
        assert filter instanceof com.unboundid.ldap.sdk.Filter
        assertEquals filter.toString(), '(&(sn=nguyen))'
        
        args = ['sn':'smith', 'givenName':'sally']
        filter = directoryService.andFilterFromArgs(args)
        assertNotNull filter
        assert filter instanceof com.unboundid.ldap.sdk.Filter
        assertEquals filter.toString(), '(&(sn=smith)(givenName=sally))'
        
    }
    
    /**
     * Test the base find method itself. This will return the actual
     * entry itself because it is "find", and not "findAll".
     */
    void testFind() {
        def args = ['sn':'evans']
        def result = directoryService.find(peopleBaseDn, args)
        
        assertNotNull result
        assert result instanceof grails.plugins.directoryservice.DirectoryServiceEntry
        assert result.getSearchResultEntry() instanceof com.unboundid.ldap.sdk.SearchResultEntry
        
        // Test the UnboundID SearchResultEntry
        assertEquals result.getAttributeValue("sn"), 'Evans'
        // Test the LdapServiceEntry
        assertEquals result.sn, 'Evans'
        
    }
    
    /**
     * The the base find using an AD Economics OU. There are a lot
     * of Jills in AD, but only one in econ.
     */
    void testFindADOU() {
        def args = ['givenName':'jill']
        def result = directoryService.find(accountsEconomicsBaseDn, args)
        
        assertNotNull result
        assert result instanceof grails.plugins.directoryservice.DirectoryServiceEntry
        assert result.getSearchResultEntry() instanceof com.unboundid.ldap.sdk.SearchResultEntry
        
        // Test the UnboundID SearchResultEntry
        assertEquals result.getAttributeValue("sn"), 'Kannel'
        // Test the LdapServiceEntry
        assertEquals result.sn, 'Kannel'
        
    }
    
    /**
     * Test the base findAll method itself.
     */
    void testFindAll() {
        def args = ['sn':'James']
        def results = directoryService.findAll(peopleBaseDn, args)
        
        assertNotNull results
        assert results[0] instanceof grails.plugins.directoryservice.DirectoryServiceEntry
        assert results[0].getSearchResultEntry() instanceof com.unboundid.ldap.sdk.SearchResultEntry
        assertEquals results[0].getAttributeValue("sn"), 'James'
        assertEquals results.size(), 4
    }
    
    /**
     * The the base findAll using an AD Economics OU. There are a lot
     * of Jills in AD, but only one in econ.
     */
    void testFindAllADOU() {
        def args = ['givenName':'jill']
        def results = directoryService.findAll(accountsEconomicsBaseDn, args)
        
        assertNotNull results
        assert results[0] instanceof grails.plugins.directoryservice.DirectoryServiceEntry
        assert results[0].getSearchResultEntry() instanceof com.unboundid.ldap.sdk.SearchResultEntry
        
        assertEquals results.size(), 1
        
        // Test the UnboundID SearchResultEntry
        assertEquals results[0].getAttributeValue("sn"), 'Kannel'
        // Test the LdapServiceEntry
        assertEquals results[0].sn, 'Kannel'
        
    }
    
    /**
     * Test findPersonWhere with a single argument.
     */
    void testFindPersonWhereSingleArg() {
        def person = directoryService.findPersonWhere('uid':'1')
        assertNotNull person
        assertEquals person.getAttributeValue("uid"), '1'
        assertEquals person.getAttributeValue("sn"), 'Williams'
    }
    
    /**
     * Test findPersonWhere with multiple arguments.
     */
    void testFindPersonWhereMultipleArgs() {
        def person = directoryService.findPersonWhere('givenName':'roland', 'sn':'conner')
        assertNotNull person
        assertEquals person.getAttributeValue("uid"), '11'
        assertEquals person.getAttributeValue("givenName"), 'Roland'
        assertEquals person.getAttributeValue("sn"), 'Conner'
    }
    
    /**
     * Test findAllPeopleWhere with a single argument.
     */
    void testFindPeopleWhereSingleArg() {
        def people = directoryService.findPeopleWhere('sn':'williams')
        assertNotNull people
        assertEquals people.size(), 4
        assertEquals people[0].getAttributeValue("sn"), 'Williams'
    }
    
    /**
     * Test findAllPeopleWhere with multiple arguments.
     */
    void testFindPeopleWhereMultipleArgs() {
        def people = directoryService.findPeopleWhere('sn':'williams', 'l':'berkeley')
        assertNotNull people
        assertEquals people.size(), 2
        assertEquals people[0].getAttributeValue("l"), 'Berkeley'
    }
    
    /**
     * Test the createFilter method.
     */
    void testCreateFilter() {
        def filter = directoryService.createFilter('(&(sn=do*))')
        assert filter instanceof LDAPFilter
        
        filter = directoryService.createFilter('(&(sn=doe)(departmentNumber=12345)(|(sn=rockwell)))')
        assert filter instanceof LDAPFilter
    }
    
    /**
     * Test the findAllUsingFilter method.
     */
    void testFindAllUsingFilter() {
        def filter = directoryService.createFilter('(&(sn=wa*))')
        assert filter instanceof LDAPFilter
        
        def people = directoryService.findAllUsingFilter(peopleBaseDn, filter)
        assertNotNull people
        assertEquals people.size(), 16
    }
    
    /**
     * Test the findAllPeopleWhere method when supplying a filter.
     */
    void testFindAllPeopleWhereUsingFilter() {
        def filter = directoryService.createFilter('(&(sn=wa*))')
        def people = directoryService.findPeopleWhere(filter)
        assertNotNull people
        assertEquals people.size(), 16
        
        filter = directoryService.createFilter('(|(sn=walters)(sn=williams))')
        println "filter: ${filter.toString()}"
        people = directoryService.findPeopleWhere(filter)
        assertNotNull people
        assertEquals people.size(), 8
    }

}