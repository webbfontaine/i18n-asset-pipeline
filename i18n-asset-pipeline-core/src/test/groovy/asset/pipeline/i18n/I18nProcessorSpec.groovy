/*
 * I18nProcessorSpec.groovy
 *
 * Copyright (c) 2014-2016, Daniel Ellermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package asset.pipeline.i18n

import asset.pipeline.AssetFile
import asset.pipeline.GenericAssetFile
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification
import spock.lang.Unroll


class I18nProcessorSpec extends Specification {

    //-- Constants ------------------------------

    protected static final String MESSAGES = '''
# Example message file
foo.foo = Test
foo.bar = Another test

special.empty =
special.backslash = Test \\\\{0\\\\}
special.crlf = This is\\n\\
        a test.
special.quotationMarks = This is a "test".
toto.suffix = Suffix
'''


    //-- Fields ---------------------------------

    AssetFile assetFile
    I18nProcessor processor


    //-- Fixture methods ------------------------

    def setup() {

        // first create a i18n processor instance using this message file
        processor = new I18nProcessor(null)
        processor.resourceLoader = Mock(ResourceLoader)
        processor.resourceLoader.getResource(_) >> new InputStreamResource(
            new ByteArrayInputStream(MESSAGES.bytes)
        )

        // then create a mock asset file
        assetFile = new GenericAssetFile(path: '/foo/bar/mymessages.i18n')
    }


    //-- Feature methods ------------------------

    def 'Handle language files correctly'() {
        given: 'an i18n processor'
        def processor = new I18nProcessor(null)

        and: 'a mocked resource'
        Resource nonExistantResource = Mock()
        nonExistantResource.exists() >> false

        and: 'a mocked resource loader'
        ResourceLoader resourceLoader = Mock()
        1 * resourceLoader.getResource('messages_de.properties') >> nonExistantResource
        1 * resourceLoader.getResource('messages_de.xml') >> nonExistantResource
        1 * resourceLoader.getResource('file:grails-app/i18n/messages_de.properties') >>
            new InputStreamResource(
                new ByteArrayInputStream(MESSAGES.bytes)
            )
        1 * resourceLoader.getResource('file:grails-app/i18n/messages_de.xml')
        processor.resourceLoader = resourceLoader

        and: 'a localized mock asset file'
        def assetFile = new GenericAssetFile(
            path: '/foo/bar/mymessages_de.i18n'
        )

        when: 'I process an empty string'
        String res = processor.process('', assetFile)

        then: 'I do not get an error message'
        '' != res
    }

    def 'Process empty i18n file is possible'() {
        when: 'I process an empty i18n file'
        String res = processor.process('', assetFile)

        then:
        checkContains('',res)
    }

    def 'Process i18n file with valid message codes is possible'() {
        when: 'I process an i18n file containing valid message codes'
        String res = processor.process('foo.bar\nfoo.foo', assetFile)

        then:
        checkContains('''        "foo.bar": "Another test",
        "foo.foo": "Test"''', res)
    }

    def 'Process i18n file with invalid message codes is possible'() {
        when: 'I process an i18n file containing valid message codes'
        String res = processor.process('foo.bar\nfoo.whee', assetFile)

        then:
        checkContains('''        "foo.bar": "Another test",
        "foo.whee": "foo.whee"''',res)
    }

    def 'Special characters have been escaped correctly'() {
        when: 'I process an i18n file containing valid message codes'
        String res = processor.process(
            '''special.backslash
special.empty
special.quotationMarks
special.crlf''',
            assetFile
        )

        then:
        checkContains(
            '''        "special.backslash": "Test \\\\{0\\\\}",
        "special.empty": "",
        "special.quotationMarks": "This is a \\"test\\".",
        "special.crlf": "This is\\na test."'''
            ,res)
    }

    @Unroll
    def 'Regex #regex as keys is possible'() {
        when: 'processing a regex'
        String res = processor.process(regex, assetFile)
        then:
        checkContains(expectedResult,res)
        where :
        regex | expectedResult
        'special\\.' | '''            "quotationMarks": "This is a \\"test\\".",
                                    "empty": "",
                                    "crlf": "This is\\na test.",
                                    "backslash": "Test \\\\{0\\\\}"'''
        'foo\\.' | '''        "bar": "Another test",
                            "foo": "Test"'''
        'sp[^\\.]*\\.'| '''            "quotationMarks": "This is a \\"test\\".",
                                    "empty": "",
                                    "crlf": "This is\\na test.",
                                    "backslash": "Test \\\\{0\\\\}"'''
        '(.*)\\.suffix' | '''"toto": "Suffix"'''
    }


    //-- Non-public methods ---------------------
    boolean checkContains(String testString, String processorResult) {
        boolean contains = true
        testString?.eachLine {
           contains &= processorResult.contains(it.trim().replaceAll(',', ''))
        }
        contains
    }

    String getJavaScriptCode(String messages) {
        StringBuilder buf = new StringBuilder('''(function (win) {
            if (win.i18n_messages) {
                var tmpMsg = {
                                ''')
        buf << messages
        buf << '''
                }
                for (var attrname in tmpMsg) { win.i18n_messages[attrname] = tmpMsg[attrname]; }
            }
            else {
                win.i18n_messages = {
                '''
        buf << messages
        buf << '''
                    };
            }
            var messages = win.i18n_messages;
            var stringFormat = function(format, prevArgs) {
                var args = Array.prototype.slice.call(prevArgs, 1);
                return format.replace(/{(\\d+)}/g, function(match, number) { 
                  return typeof args[number] != 'undefined\'
                    ? args[number] 
                    : match
                  ;
                });
             };
            win.$L = function (code) {
                var message = messages[code];
                if(message === undefined) {
                    return "[" + code + "]";
                } else {
                    return stringFormat(message, arguments);
                }
            };
            win.msg = function(code) {
               var message = messages[code];
                if(message === undefined) {
                    return "[" + code + "]";
                } else {
                    return stringFormat(message, arguments);
                }
            };
        }(this));
        '''

        buf.toString()
    }
}
