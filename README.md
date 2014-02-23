# AntennaPod Single Purpose

## What is AntennaPod SP?

AntennaPod SP is a modified version of AntennaPod which lets podcasters build *single-purpose apps* for their podcast(s).

## What is a single-purpose app?

A single-purpose app is an app that lets the user only do one thing: Listen to a preset list of podcasts. There is absolutely *no* setup or configuration process. As soon as the user starts the app, a list of episodes is presented that can be downloaded and listened to.

## Who is a single-purpose app for?

A single-purpose app that is based on AntennaPod SP is specifically made for people who 

- have never listened to podcasts before

- occasionally listen to podcast, but have never used a full-fledged podcatcher before.

AntennaPod SP's main goal is to be *easy to use*. Therefore, it only focuses on the basic features for consuming podcasts and requires absolutely *no configuration* by the user.

## What features does a single-purpose app have?

- Download and playback of episodes
- Automatic download of the most recent episodes
- Automatic update of the feed(s) every n hours
- Automatic deletion of episodes after the amount of space used by the app exceeds a given limit
- Support for displaying shownotes

All important parameters of these features can be adjusted by the podcaster in a global configuration file.

*Please note that support for multiple feeds is not finished yet, but will be implemented very soon*

There are also plans for adding the following (optional) features:

- support for chapters
- support for feeds and episodes that require authentication
- and more...



## But what if the user gets fed up by all these single-purpose apps that you have to install for every single podcast?

Since a single-purpose app are supposed to attract *new* podcast listeners, there should be a way to let them switch to something more sophisticated once they become more interested into podcasts. Therefore, a single-purpose app looks for other installed single-purpose apps on the user's device once it is started. If other single-purpose apps are found, the user is given the choice to install podcatcher (by default, this podcatcher is [AntennaPod](https://github.com/danieloeh/AntennaPod), but this can also be changed in the configuration file by the podcaster). If the user accepts, all feeds from single-purpose apps are subscribed to by the podcatcher once it is installed.

## Who is using AntennaPod SP?

So far, the app for listening to the [Einschlafen Podcast](http://einschlafen-podcast.de/) is based on AntennaPod SP. Its source code can be found [here]().


## How do i build an app for my podcasts that is based on AntennaPod SP?


Basically, you need to fork this repository, install an Android SDK, modify two configuration files to enter your feeds and the name of your podcast, and add icons and assets for your app. A detailed guide on how to build your very own single-purpose app can be found in the Wiki (TODO).


## License

AntennaPod SP is licensed under the MIT License. You can find the license text in the LICENSE file.


## Donate
  
Bitcoin donations can be sent to this address: <pre>1DzvtuvdW8VhDsq9GUytMyALmsHeaHEKbg</pre>

