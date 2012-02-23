/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.ircbot.internal;

import java.util.List;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.ircbot.IRCBot;

/**
 * Stub IRC Bot for the functional tests. Extends the real IRC Bot implementation but ensures that nothing is sent
 * on the wire.
 *
 * @version $Id$
 * @since 4.0M1
 */
@Component(roles = {IRCBot.class})
@Singleton
public class StubIRCBot extends PircBotIRCBot implements Initializable
{
    @Override
    public void initialize() throws InitializationException
    {
        this.pircBot = new StubPircBot(this);
    }

    public List<String> getMessages()
    {
        return ((StubPircBot) this.pircBot).getMessages();
    }
}
